package moe.notice.filter.xposed

import android.content.ContentValues
import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.concurrent.Executors
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import moe.notice.filter.BuildConfig
import moe.notice.filter.InboxChannel
import moe.notice.filter.FilterPrefs
import moe.notice.filter.data.FilterConfigCodec
import moe.notice.filter.data.NotificationDetailsCodec
import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.NotificationDetails
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.RuleMatcher
import moe.notice.filter.provider.NotificationLogProvider

internal class KeywordFilter {
    private val lock = Any()
    private var prefs: XSharedPreferences? = null
    private var lastConfigSummary = ""
    @Volatile private var config = FilterConfig()

    fun shouldBlock(args: Array<Any?>, context: Context?): Boolean {
        ensurePrefs()

        var pkg: String? = null
        var notification: Notification? = null
        for (arg in args) {
            when (arg) {
                is Notification -> notification = arg
                is String -> if (pkg == null && arg.contains('.')) pkg = arg
            }
        }
        val n = notification ?: return false
        if (n.extras?.getBoolean(BlockedInbox.EXTRA_MARKER) == true) return false
        if (n.channelId == InboxChannel.ID) return false
        if (isCritical(n)) return false

        val resolved = Xiaomi.resolvePackage(pkg, n)
        if (resolved in PROTECTED_PACKAGES) return false

        val extracted = NotificationText.extract(n)
        val hit = if (!config.enabled) {
            null
        } else {
            RuleMatcher.firstMatch(config.rules, resolved, extracted.combined)
        }
        XposedBridge.log(formatJudgeLog(resolved, extracted.combined, hit))
        if (config.logEnabled) {
            try {
                if (hit != null || extracted.combined.isNotEmpty()) {
                    val details = runCatching { NotificationCapture.capture(n, args) }
                        .getOrDefault(NotificationDetails())
                    log(context, resolved, extracted, hit, details)
                }
            } catch (t: Throwable) {
                XposedBridge.log("Notice: log failed: $t")
            }
        }
        return hit != null
    }

    private fun formatJudgeLog(pkg: String, text: String, hit: BlockRule?): String {
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
        return "Notice: judge enabled=${config.enabled} rules={$rules} pkg=$pkg result=$result text=$snippet"
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
            LOG_EXECUTOR.execute {
                try {
                    ctx.contentResolver.insert(NotificationLogProvider.CONTENT_URI, values)
                } catch (t: Throwable) {
                    XposedBridge.log("Notice: log insert failed: $t")
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("Notice: log schedule failed: $t")
        }
    }

    private fun ensurePrefs() {
        synchronized(lock) {
            if (prefs != null) return
            try {
                val created = XSharedPreferences(BuildConfig.APPLICATION_ID, FilterPrefs.NAME)
                created.reload()
                XposedBridge.log(
                    "Notice: prefs file=" + created.file +
                        " canRead=" + created.file.canRead() +
                        " keys=" + created.all.keys,
                )
                created.registerOnSharedPreferenceChangeListener { shared, _ ->
                    try {
                        val xp = shared as? XSharedPreferences ?: created
                        xp.reload()
                        config = FilterConfigCodec.fromPrefs(xp)
                        logConfig("prefs changed file=" + xp.file)
                    } catch (t: Throwable) {
                        XposedBridge.log("Notice: prefs listener failed: $t")
                    }
                }
                prefs = created
                config = FilterConfigCodec.fromPrefs(created)
                logConfig("from prefs file=" + created.file)
            } catch (t: Throwable) {
                XposedBridge.log("Notice: load prefs failed: $t")
            }
        }
    }

    private fun logConfig(source: String) {
        val summary = "enabled=${config.enabled} log=${config.logEnabled} rules=${config.rules.size} $source"
        if (summary == lastConfigSummary) return
        lastConfigSummary = summary
        XposedBridge.log("Notice: config $summary")
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
        val LOG_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "notice-log").apply { isDaemon = true }
        }

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
