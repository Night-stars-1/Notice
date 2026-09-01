package moe.notice.filter.xposed

import android.content.ContentValues
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var reloadReceiverRegistered = false
    private val sink = LogSink()

    /** 配置每次加载 / 更新后回调（在调用线程上执行）。 */
    var onConfigChanged: ((FilterConfig) -> Unit)? = null
    val inboxEnabled: Boolean get() = config.inboxEnabled
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
            val l = SharedPreferences.OnSharedPreferenceChangeListener { p, _ ->
                config = FilterConfigCodec.fromPrefs(p)
                logConfig("remote update")
                refreshModel()
            }
            listener = l
            prefs.registerOnSharedPreferenceChangeListener(l)
        } catch (t: Throwable) {
            Xp.log("remote preferences unavailable, filter stays empty", t)
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
            Xp.log("spam model missing from resources")
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
                        Xp.log("spam delta v$version loaded: ${delta.indices.size} weights")
                    }
                } else {
                    Xp.log("spam delta v$version not found, using bundled model")
                }
            } catch (t: Throwable) {
                Xp.log("spam delta load failed, using bundled model", t)
            }
        }
        model = next
        loadedDeltaVersion = version
    }

    /** 重新从框架拉取远程偏好；由应用保存配置后的广播触发。 */
    fun reload(source: String) {
        val a = api ?: return
        try {
            config = FilterConfigCodec.fromPrefs(a.getRemotePreferences(FilterPrefs.NAME))
            logConfig(source)
            refreshModel()
        } catch (t: Throwable) {
            Xp.log("reload config failed", t)
        }
    }

    private fun ensureReloadReceiver(ctx: Context) {
        if (reloadReceiverRegistered) return
        reloadReceiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                reload("broadcast")
            }
        }
        try {
            val filter = IntentFilter(FilterPrefs.ACTION_RELOAD)
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(receiver, filter)
            }
        } catch (t: Throwable) {
            Xp.log("register reload receiver failed", t)
        }
    }

    fun shouldBlock(args: Array<Any?>, context: Context?): Boolean {
        if (context != null) {
            DebugLog.attach(context, sink)
            ensureReloadReceiver(context)
        }

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
        val n = notification ?: return false
        if (n.extras?.getBoolean(BlockedInbox.EXTRA_MARKER) == true) return false
        if (n.channelId == InboxChannel.ID) return false
        if (isCritical(n)) return false

        val resolved = Xiaomi.resolvePackage(pkg, n)
        if (resolved in PROTECTED_PACKAGES) return false

        val extracted = NotificationText.extract(n)
        val cfg = config
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
                Xp.log("spam model failed", t)
                null
            }
            if (hit == null && verdict?.block == true) hit = SpamJudge.rule
        }
        // 日志里显示的规则：拦截它的那条，否则放行它的那条（白名单）。
        val shownRule = hit ?: decision.allow
        if (cfg.judgeLogEnabled) {
            Xp.log(formatJudgeLog(resolved, extracted.combined, hit, shownRule, verdict, decision.skipAi))
        }
        if (config.logEnabled) {
            try {
                if (shownRule != null || extracted.combined.isNotEmpty()) {
                    val details = runCatching { NotificationCapture.capture(n, args) }
                        .getOrDefault(NotificationDetails())
                        .copy(spamScore = verdict?.score, spamProtected = verdict?.protected == true)
                    log(context, resolved, extracted, hit != null, shownRule, details)
                }
            } catch (t: Throwable) {
                Xp.log("log failed", t)
            }
        }
        return hit != null
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
            Xp.log("log schedule failed", t)
        }
    }

    private fun logConfig(source: String) {
        DebugLog.enabled = config.debugLogEnabled
        try {
            onConfigChanged?.invoke(config)
        } catch (t: Throwable) {
            Xp.log("config callback failed", t)
        }
        val summary = "enabled=${config.enabled} log=${config.logEnabled} inbox=${config.inboxEnabled} spam=${config.spamEnabled}@${config.spamThreshold} delta=${config.spamDeltaVersion} excluded=${config.spamExcludedPackages.size} rules=${config.rules.size} $source"
        if (summary == lastConfigSummary) return
        lastConfigSummary = summary
        Xp.log("config $summary")
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
