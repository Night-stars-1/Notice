package moe.notice.filter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object TestNotifier {
    private const val CHANNEL_ID = "notice_self_test"
    private const val NOTIFY_ID = 1001

    fun send(context: Context, keyword: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "拦截测试",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Notice 测试")
            .setContentText("关键词：$keyword")
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFY_ID, notification)
        } catch (_: RuntimeException) {
            // Hooked NMS may skip posting; the client should not crash.
        }
    }
}
