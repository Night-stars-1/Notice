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

    fun shouldBlock(args: Array<Any?>, context: Context?): Boolean {

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
        val ruleHit = if (!cfg.enabled) {
            null
        } else {
            RuleMatcher.firstMatch(cfg.rules, resolved, extracted.combined)
        }
        var verdict: SpamJudge.Verdict? = null
        var hit = ruleHit
        if (cfg.enabled && cfg.spamEnabled && resolved !in cfg.spamExcludedPackages) {
            // 即使规则已经命中也进行评分，以便每条日志都带有分数。
            verdict = try {
                if (model == null) refreshModel()
                model?.let { SpamJudge.judge(it, cfg.spamThreshold, extracted.combined) }
            } catch (t: Throwable) {
                Xp.log("spam model failed", t)
                null
            }
            if (hit == null && verdict?.block == true) hit = SpamJudge.rule
        }
        Xp.log(formatJudgeLog(resolved, extracted.combined, hit, verdict))
        if (config.logEnabled) {
            try {
                if (hit != null || extracted.combined.isNotEmpty()) {
                    val details = runCatching { NotificationCapture.capture(n, args) }
                        .getOrDefault(NotificationDetails())
                        .copy(spamScore = verdict?.score, spamProtected = verdict?.protected == true)
                    log(context, resolved, extracted, hit, details)
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
        verdict: SpamJudge.Verdict?,
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
            "$name/$on/${rule.mode.id}/[$keys]$exclude"
        }.ifBlank { "(none)" }
        val snippet = clipText(text)
        val result = if (hit == null) "allow" else "block:" + hit.name.ifBlank { hit.id }
        val spam = when {
            verdict == null -> ""
            verdict.protected -> " spam=%.3f(protected)".format(java.util.Locale.ROOT, verdict.score)
            else -> " spam=%.3f".format(java.util.Locale.ROOT, verdict.score)
        }
        return "judge enabled=${config.enabled} spamEnabled=${config.spamEnabled} rules={$rules} pkg=$pkg result=$result$spam text=$snippet"
    }

    private fun clipText(text: String): String {
        val snippet = text.lineSequence().joinToString(" ").trim()
        return if (snippet.length <= 500) snippet else snippet.take(500) + "..."
    }

    private fun log(
        context: Context?,
        packageName: String,
        extracted: NotificationText.Extracted,
        hit: BlockRule?,
        details: NotificationDetails,
    ) {
        val ctx = context ?: return
        val values = ContentValues().apply {
            put(NotificationLogProvider.COL_PACKAGE, packageName)
            put(NotificationLogProvider.COL_TITLE, extracted.title)
            put(NotificationLogProvider.COL_TEXT, extracted.body)
            put(NotificationLogProvider.COL_TIMESTAMP, System.currentTimeMillis())
            put(NotificationLogProvider.COL_BLOCKED, if (hit != null) 1 else 0)
            put(NotificationLogProvider.COL_RULE_ID, hit?.id)
            put(NotificationLogProvider.COL_RULE_NAME, hit?.name)
            put(NotificationLogProvider.COL_DETAILS, NotificationDetailsCodec.toJson(details))
        }
        try {
            sink.submit(ctx, values)
        } catch (t: Throwable) {
            Xp.log("log schedule failed", t)
        }
    }

    private fun logConfig(source: String) {
        val summary = "enabled=${config.enabled} log=${config.logEnabled} spam=${config.spamEnabled}@${config.spamThreshold} delta=${config.spamDeltaVersion} excluded=${config.spamExcludedPackages.size} rules=${config.rules.size} $source"
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
