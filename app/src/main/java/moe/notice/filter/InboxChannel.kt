package moe.notice.filter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object InboxChannel {
    const val ID = "notice_blocked_inbox"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                ID,
                context.getString(R.string.blocked_inbox_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.blocked_inbox_channel_desc)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
            },
        )
    }
}
