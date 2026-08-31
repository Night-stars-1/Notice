package moe.notice.filter.xposed

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import moe.notice.filter.BuildConfig
import moe.notice.filter.provider.NotificationLogProvider

/**
 * Delivers notification logs to the app's ContentProvider, the only persistent store the app
 * can read. Inserting starts the app process when it is not running, so entries are batched:
 * written at once while the app is alive, otherwise buffered and flushed after [FLUSH_DELAY_MS]
 * or once [FLUSH_THRESHOLD] entries pile up, whichever comes first. The app also broadcasts
 * [ACTION_FLUSH] on start to collect whatever is pending.
 */
internal class LogSink {
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "notice-log").apply { isDaemon = true }
    }
    private val pending = ArrayDeque<ContentValues>()
    private var scheduled: ScheduledFuture<*>? = null
    private var receiverRegistered = false

    fun submit(ctx: Context, values: ContentValues) {
        worker.execute {
            ensureReceiver(ctx)
            pending.addLast(values)
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

    // Runs on [worker].
    private fun flush(ctx: Context) {
        scheduled?.cancel(false)
        scheduled = null
        while (pending.isNotEmpty()) {
            val values = pending.removeFirst()
            try {
                ctx.contentResolver.insert(NotificationLogProvider.CONTENT_URI, values)
            } catch (t: Throwable) {
                Xp.log("log insert failed", t)
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
            Xp.log("query running processes failed", t)
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
            Xp.log("register flush receiver failed", t)
        }
    }

    companion object {
        const val ACTION_FLUSH = "moe.notice.filter.FLUSH_LOGS"
        const val FLUSH_DELAY_MS = 30_000L
        const val FLUSH_THRESHOLD = 50
        const val MAX_PENDING = 2_000
    }
}
