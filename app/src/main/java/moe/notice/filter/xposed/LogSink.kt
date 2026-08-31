package moe.notice.filter.xposed

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import moe.notice.filter.BuildConfig
import moe.notice.filter.provider.NotificationLogProvider

/**
 * 将通知日志投递到应用的 ContentProvider，这是应用唯一能读取的持久化存储。
 * 应用未运行时执行插入会拉起应用进程，因此条目按批处理：应用存活时立即写入，
 * 否则先缓冲，待 [FLUSH_DELAY_MS] 到期或累积到 [FLUSH_THRESHOLD] 条后再刷出，
 * 以先到者为准。应用启动时也会广播 [ACTION_FLUSH]，以收集所有待处理的条目。
 */
internal class LogSink {
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "notice-log").apply { isDaemon = true }
    }
    private val pending = ArrayDeque<Pair<Uri, ContentValues>>()
    private var scheduled: ScheduledFuture<*>? = null
    private var receiverRegistered = false

    fun submit(ctx: Context, values: ContentValues, uri: Uri = NotificationLogProvider.CONTENT_URI) {
        worker.execute {
            ensureReceiver(ctx)
            pending.addLast(uri to values)
            while (pending.size > MAX_PENDING) pending.removeFirst()
            when {
                appRunning(ctx) || pending.size >= FLUSH_THRESHOLD -> flush(ctx)
                scheduled == null -> scheduled = worker.schedule(
                    { flush(ctx) },
                    FLUSH_DELAY_MS,
                    TimeUnit.MILLISECONDS,
                )
            }
        }
    }

    // 在 [worker] 上运行。
    private fun flush(ctx: Context) {
        scheduled?.cancel(false)
        scheduled = null
        while (pending.isNotEmpty()) {
            val (uri, values) = pending.removeFirst()
            try {
                ctx.contentResolver.insert(uri, values)
            } catch (t: Throwable) {
                Xp.log("log insert failed", t, mirror = false)
            }
        }
    }

    private fun appRunning(ctx: Context): Boolean {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.any { proc ->
                proc.pkgList?.contains(BuildConfig.APPLICATION_ID) == true
            } == true
        } catch (t: Throwable) {
            Xp.log("query running processes failed", t, mirror = false)
            false
        }
    }

    private fun ensureReceiver(ctx: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                worker.execute { flush(ctx) }
            }
        }
        try {
            val filter = IntentFilter(ACTION_FLUSH)
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(receiver, filter)
            }
        } catch (t: Throwable) {
            Xp.log("register flush receiver failed", t, mirror = false)
        }
    }

    companion object {
        const val ACTION_FLUSH = "moe.notice.filter.FLUSH_LOGS"
        const val FLUSH_DELAY_MS = 30_000L
        const val FLUSH_THRESHOLD = 50
        const val MAX_PENDING = 2_000
    }
}
