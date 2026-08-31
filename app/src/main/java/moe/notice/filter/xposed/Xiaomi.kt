package moe.notice.filter.xposed

import android.app.Notification

internal object Xiaomi {
    fun resolvePackage(pkg: String?, notification: Notification): String {
        try {
            val extra = notification.javaClass.getField("extraNotification").get(notification)
            if (extra != null) {
                val target = extra.javaClass.methods
                    .firstOrNull { it.name == "getTargetPkg" && it.parameterCount == 0 }
                    ?.invoke(extra) as? String
                if (!target.isNullOrBlank()) return target
            }
        } catch (_: Throwable) {
            // AOSP Notification has no extraNotification; ignore.
        }
        return pkg.orEmpty()
    }
}
