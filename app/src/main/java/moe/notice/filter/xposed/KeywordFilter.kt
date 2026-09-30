package moe.notice.filter.xposed

import android.content.ContentValues
import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.libxposed.api.XposedInterface
import moe.notice.filter.FilterPrefs
import moe.notice.filter.InboxChannel
import moe.notice.filter.data.FilterConfigCodec
import moe.notice.filter.data.NotificationDetailsCodec
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.NotificationDetails
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.RuleMatcher
import moe.notice.filter.domain.SpamDelta
import moe.notice.filter.domain.SpamJudge
import moe.notice.filter.domain.SpamModel
import moe.notice.filter.provider.NotificationLogProvider

internal class KeywordFilter {
    private var lastConfigSummary = ""
    @Volatile private var config = FilterConfig()
    /** 已应用用户调优增量的内置模型；加载前为 null。 */
    @Volatile private var model: SpamModel? = null
    @Volatile private var loadedDeltaVersion = -1L
    private var api: XposedInterface? = null
    private val sink = LogSink()
    /**
     * 进度类通知（下载条等）每秒会更新很多次，每次都跑规则、AI 和跨进程上报会拖慢状态栏。
     * 这里按「包名 / id / tag」缓存上一次的判定结果：后续进度更新直接复用结论，
     * 并把记录上报限制为每 [PROGRESS_LOG_INTERVAL_MS] 最多一次。
     */
    private val progressCache = object : LinkedHashMap<String, ProgressEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProgressEntry>?): Boolean =
            size > PROGRESS_CACHE_SIZE
    }

    private class ProgressEntry(
        val outcome: Outcome,
        val hit: BlockRule?,
        val shownRule: BlockRule?,
        val verdict: SpamJudge.Verdict?,
        var lastLoggedAt: Long,
        var lastSeenAt: Long,
    )
    // 强引用持有：SharedPreferences 的实现以弱引用保存监听器。
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** 从框架的远程偏好设置中读取规则，并跟随守护进程推送的更新。 */
    fun attach(api: XposedInterface) {
        this.api = api
        try {
            val prefs = api.getRemotePreferences(FilterPrefs.NAME)
            config = FilterConfigCodec.fromPrefs(prefs)
            logConfig("remote")
            refreshModel()
            val l = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                val fromPrefs = FilterConfigCodec.fromPrefs(p)
                // 不去重的诊断日志：确认回调是否触发，以及回调时读到的是不是新值
                Xp.log("远程偏好回调触发 key=$key delta=${fromPrefs.spamDeltaVersion} rules=${fromPrefs.rules.size}")
                config = fromPrefs
                logConfig("remote update")
                refreshModel()
            }
            listener = l
            prefs.registerOnSharedPreferenceChangeListener(l)
        } catch (t: Throwable) {
            Xp.log("远程偏好不可用，过滤配置保持为空", t)
        }
    }

    /** 当配置中的调优增量版本发生变化时重建 [model]。 */
    private fun refreshModel() {
        val cfg = config
        if (!cfg.spamEnabled && model == null) return // 惰性加载：目前还没有需要评分的内容
        val version = cfg.spamDeltaVersion
        if (version == loadedDeltaVersion && model != null) return
        val base = SpamModel.bundled()
        if (base == null) {
            Xp.log("资源里没有内置模型")
            model = null
            loadedDeltaVersion = version
            return
        }
        var next = base
        if (version != 0L) {
            try {
                val pfd = api?.openRemoteFile(SpamDelta.REMOTE_FILE)
                if (pfd != null) {
                    ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                        val delta = SpamDelta.decode(input)
                        next = base.withDelta(delta)
                        Xp.log("已加载微调量 v$version：${delta.indices.size} 个权重")
                    }
                } else {
                    Xp.log("未找到微调量 v$version，使用内置模型")
                }
            } catch (t: Throwable) {
                Xp.log("微调量加载失败，使用内置模型", t)
            }
        }
        model = next
        loadedDeltaVersion = version
    }

    /** 一次判定的结果：是否拦截，以及拦截后是否放入收件箱通知（规则可选择静默拦截）。 */
    data class Outcome(val block: Boolean, val notify: Boolean) {
        companion object {
            val PASS = Outcome(block = false, notify = false)
        }
    }

    fun shouldBlock(args: Array<Any?>, context: Context?): Outcome {
        if (context != null) DebugLog.attach(context, sink)

        var pkg: String? = null
        var notification: Notification? = null
        for (arg in args) {
            when (arg) {
                is Notification -> notification = arg
                is String -> if (pkg == null && arg.contains('.')) pkg = arg
            }
        }
        if (pkg == null) {
            // 诸如 "android" 之类的系统包名不含点号；enqueue 的第一个 String 参数即为包名。
            pkg = args.firstOrNull { it is String && it.isNotBlank() } as? String
        }
        val n = notification ?: return Outcome.PASS
        if (n.extras?.getBoolean(BlockedInbox.EXTRA_MARKER) == true) return Outcome.PASS
        if (n.channelId == InboxChannel.ID) return Outcome.PASS
        if (isCritical(n)) return Outcome.PASS

        val resolved = Xiaomi.resolvePackage(pkg, n)
        if (resolved in PROTECTED_PACKAGES) return Outcome.PASS

        val progressKey = if (hasProgressBar(n)) progressKey(resolved, args) else null
        val cfg = config
        if (progressKey != null) {
            val now = System.currentTimeMillis()
            val cached = synchronized(progressCache) {
                progressCache[progressKey]?.takeIf { now - it.lastSeenAt < PROGRESS_CACHE_TTL_MS }
            }
            if (cached != null) {
                cached.lastSeenAt = now
                if (cfg.logEnabled && now - cached.lastLoggedAt >= PROGRESS_LOG_INTERVAL_MS) {
                    cached.lastLoggedAt = now
                    logRecord(context, n, args, resolved, NotificationText.extract(n), cached.hit, cached.shownRule, cached.verdict)
                }
                return cached.outcome
            }
        }

        val extracted = NotificationText.extract(n)
        val decision = if (!cfg.enabled) {
            RuleMatcher.Decision()
        } else {
            RuleMatcher.evaluate(cfg.rules, resolved, extracted.combined)
        }
        var verdict: SpamJudge.Verdict? = null
        var hit = decision.block
        val aiAllowed = cfg.enabled && cfg.spamEnabled &&
            resolved !in cfg.spamExcludedPackages &&
            decision.allow == null && !decision.skipAi
        if (aiAllowed) {
            // 规则已命中拦截时也打分，让每条记录都带分数。
            verdict = try {
                if (model == null) refreshModel()
                model?.let { SpamJudge.judge(it, cfg.spamThreshold, extracted.combined) }
            } catch (t: Throwable) {
                Xp.log("骚扰识别打分失败", t)
                null
            }
            if (hit == null && verdict?.block == true) hit = SpamJudge.rule
        }
        // 日志里显示的规则：拦截它的那条，否则放行它的那条（白名单）。
        val shownRule = hit ?: decision.allow
        if (cfg.judgeLogEnabled) {
            Xp.log(formatJudgeLog(resolved, extracted.combined, hit, shownRule, verdict, decision.skipAi))
        }
        if (cfg.logEnabled) logRecord(context, n, args, resolved, extracted, hit, shownRule, verdict)
        val outcome = Outcome(block = hit != null, notify = hit?.notify ?: false)
        if (progressKey != null) {
            val now = System.currentTimeMillis()
            synchronized(progressCache) {
                progressCache[progressKey] = ProgressEntry(outcome, hit, shownRule, verdict, lastLoggedAt = now, lastSeenAt = now)
            }
        }
        return outcome
    }

    private fun logRecord(
        context: Context?,
        n: Notification,
        args: Array<Any?>,
        resolved: String,
        extracted: NotificationText.Extracted,
        hit: BlockRule?,
        shownRule: BlockRule?,
        verdict: SpamJudge.Verdict?,
    ) {
        try {
            if (shownRule != null || extracted.combined.isNotEmpty()) {
                val details = runCatching { NotificationCapture.capture(n, args) }
                    .getOrDefault(NotificationDetails())
                    .copy(spamScore = verdict?.score, spamProtected = verdict?.protected == true)
                log(context, resolved, extracted, hit != null, shownRule, details)
            }
        } catch (t: Throwable) {
            Xp.log("记录通知日志失败", t)
        }
    }

    /** 与 NotificationCapture.progress 的口径一致：只有 max > 0 或不确定进度才算进度条。 */
    private fun hasProgressBar(n: Notification): Boolean {
        val extras = n.extras ?: return false
        if (!extras.containsKey(Notification.EXTRA_PROGRESS)) return false
        return extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
            extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
    }

    /** enqueueNotificationInternal 的参数里 tag、id 紧挨在 Notification 前面。 */
    private fun progressKey(pkg: String, args: Array<Any?>): String? {
        val index = args.indexOfFirst { it is Notification }
        if (index < 1) return null
        val id = args[index - 1] as? Int ?: return null
        val tag = if (index >= 2) args[index - 2] as? String else null
        return "$pkg#$id#${tag.orEmpty()}"
    }

    private fun formatJudgeLog(
        pkg: String,
        text: String,
        hit: BlockRule?,
        shownRule: BlockRule?,
        verdict: SpamJudge.Verdict?,
        skipAi: Boolean,
    ): String {
        val rules = config.rules.joinToString("; ") { rule ->
            val name = rule.name.ifBlank { rule.id }
            val on = if (rule.enabled) "on" else "off"
            val keys = rule.keywords.joinToString(",")
            val exclude = if (rule.excludeKeywords.isEmpty()) {
                ""
            } else {
                " exclude=" + rule.excludeKeywords.joinToString(",")
            }
            "$name/$on/${rule.action.id}/${rule.mode.id}/[$keys]$exclude"
        }.ifBlank { "(none)" }
        val snippet = clipText(text)
        val result = when {
            hit != null -> "block:" + hit.name.ifBlank { hit.id }
            shownRule != null -> "allow:" + shownRule.name.ifBlank { shownRule.id }
            else -> "allow"
        }
        val ai = if (skipAi) " ai=skipped" else ""
        val spam = when {
            verdict == null -> ""
            verdict.protected -> " spam=%.3f(protected)".format(java.util.Locale.ROOT, verdict.score)
            else -> " spam=%.3f".format(java.util.Locale.ROOT, verdict.score)
        }
        return "judge enabled=${config.enabled} spamEnabled=${config.spamEnabled} rules={$rules} pkg=$pkg result=$result$spam$ai text=$snippet"
    }

    private fun clipText(text: String): String {
        val snippet = text.lineSequence().joinToString(" ").trim()
        return if (snippet.length <= 500) snippet else snippet.take(500) + "..."
    }

    private fun log(
        context: Context?,
        packageName: String,
        extracted: NotificationText.Extracted,
        blocked: Boolean,
        rule: BlockRule?,
        details: NotificationDetails,
    ) {
        val ctx = context ?: return
        val values = ContentValues().apply {
            put(NotificationLogProvider.COL_PACKAGE, packageName)
            put(NotificationLogProvider.COL_TITLE, extracted.title)
            put(NotificationLogProvider.COL_TEXT, extracted.body)
            put(NotificationLogProvider.COL_TIMESTAMP, System.currentTimeMillis())
            put(NotificationLogProvider.COL_BLOCKED, if (blocked) 1 else 0)
            put(NotificationLogProvider.COL_RULE_ID, rule?.id)
            put(NotificationLogProvider.COL_RULE_NAME, rule?.name)
            put(NotificationLogProvider.COL_DETAILS, NotificationDetailsCodec.toJson(details))
        }
        try {
            sink.submit(ctx, values)
        } catch (t: Throwable) {
            Xp.log("日志投递排队失败", t)
        }
    }

    private fun logConfig(source: String) {
        DebugLog.enabled = config.debugLogEnabled
        val summary = "enabled=${config.enabled} log=${config.logEnabled} spam=${config.spamEnabled}@${config.spamThreshold} delta=${config.spamDeltaVersion} excluded=${config.spamExcludedPackages.size} rules=${config.rules.size} $source"
        if (summary == lastConfigSummary) return
        lastConfigSummary = summary
        Xp.log("配置 $summary")
    }

    private fun isCritical(notification: Notification): Boolean {
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return true
        if (userInitiatedJob(notification)) return true
        when (notification.category) {
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            -> return true
        }
        val template = notification.extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("CallStyle") || template.contains("MediaStyle")
    }

    private fun userInitiatedJob(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return try {
            val flag = Notification::class.java.getField("FLAG_USER_INITIATED_JOB").getInt(null)
            notification.flags and flag != 0
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val PROGRESS_CACHE_SIZE = 64
        /** 进度通知停止更新多久后，下一次更新重新完整判定。 */
        const val PROGRESS_CACHE_TTL_MS = 60_000L
        /** 同一条进度通知两次记录上报之间的最小间隔。 */
        const val PROGRESS_LOG_INTERVAL_MS = 3_000L
        val PROTECTED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.xiaomi.finddevice",
        )
    }
}
