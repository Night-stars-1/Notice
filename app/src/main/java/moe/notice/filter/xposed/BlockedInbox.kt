package moe.notice.filter.xposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import moe.notice.filter.BuildConfig
import moe.notice.filter.InboxChannel
import moe.notice.filter.MainActivity
import moe.notice.filter.R
import java.lang.reflect.Method
import java.util.LinkedHashMap

internal class BlockedInbox {
    private val items = LinkedHashMap<String, BlockedItem>()
    private val lock = Any()
    private var nms: Any? = null
    private var context: Context? = null
    private var pkgContext: Context? = null
    private var attached = false

    fun attach(service: Any, ctx: Context?) {
        nms = service
        if (ctx == null) return
        context = ctx
        if (attached) return
        attached = true
        try {
            pkgContext = ctx.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY,
            )
        } catch (t: Throwable) {
            Xp.log("package context failed", t)
        }
        val filter = IntentFilter(ACTION_UNDO)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val key = intent?.getStringExtra(EXTRA_KEY) ?: return
                undo(key)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(receiver, filter)
            }
        } catch (t: Throwable) {
            Xp.log("register undo receiver failed", t)
        }
    }

    fun onBlocked(method: Method, service: Any?, hookArgs: Array<Any?>) {
        val notification = hookArgs.firstOrNull { it is Notification } as? Notification ?: return
        val parsed = parseArgs(hookArgs) ?: return
        if (notification.extras?.getBoolean(EXTRA_MARKER) == true) return
        if (notification.channelId == InboxChannel.ID) return
        val extracted = NotificationText.extract(notification)
        val key = "${parsed.pkg}|${parsed.tag}|${parsed.id}"
        val args = hookArgs.copyOf()
        for (i in args.indices) {
            val value = args[i]
            if (value is Notification) {
                try {
                    args[i] = value.clone()
                } catch (_: Throwable) {
                    args[i] = value
                }
            }
        }
        val item = BlockedItem(
            key = key,
            pkg = parsed.pkg,
            title = extracted.title,
            text = extracted.body,
            method = method,
            nms = service,
            args = args,
        )
        synchronized(lock) {
            items.remove(key)
            items[key] = item
            while (items.size > MAX_ITEMS) {
                val oldest = items.keys.first()
                items.remove(oldest)
            }
        }
        publish()
    }

    fun undo(key: String) {
        val item = synchronized(lock) { items.remove(key) } ?: return
        restore(item)
        publish()
    }

    private fun restore(item: BlockedItem) {
        val method = item.method ?: return
        val target = item.nms ?: nms ?: return
        val task = Runnable {
            try {
                Xp.invokeOrigin(method, target, item.args)
            } catch (t: Throwable) {
                Xp.log("restore failed", t)
            }
        }
        val handler = nmsHandler(target)
        if (handler != null) handler.post(task) else Handler(Looper.getMainLooper()).post(task)
    }

    private fun publish() {
        val snapshot = synchronized(lock) { ArrayList(items.values) }
        val ctx = context ?: return
        val appCtx = pkgContext ?: ctx
        try {
            ensureChannel(ctx, appCtx)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (snapshot.isEmpty()) {
                cancelSummary(nm)
                return
            }
            val notification = buildNotification(appCtx, ctx, snapshot)
            postSummary(nm, notification)
        } catch (t: Throwable) {
            Xp.log("publish inbox failed", t)
        }
    }

    private fun buildNotification(appCtx: Context, sysCtx: Context, snapshot: List<BlockedItem>): Notification {
        val visible = snapshot.takeLast(MAX_ROWS).reversed()
        val summary = appCtx.getString(R.string.blocked_inbox_summary, snapshot.size)
        val collapsed = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.notice_inbox_collapsed)
        collapsed.setTextViewText(R.id.inbox_summary, summary)
        val expanded = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.notice_inbox_expanded)
        expanded.removeAllViews(R.id.inbox_rows)
        for (item in visible) {
            val row = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.notice_inbox_row)
            val label = appLabel(sysCtx, item.pkg)
            val title = if (item.title.isBlank()) label else "$label · ${item.title}"
            row.setTextViewText(R.id.row_title, title)
            row.setTextViewText(R.id.row_text, item.text.ifBlank { " " })
            row.setOnClickPendingIntent(R.id.row_undo, undoIntent(sysCtx, item.key))
            expanded.addView(R.id.inbox_rows, row)
        }
        expanded.setTextViewText(R.id.inbox_view_filtered, appCtx.getString(R.string.blocked_inbox_view))
        expanded.setOnClickPendingIntent(R.id.inbox_view_filtered, openLogsIntent(appCtx))
        val builder = Notification.Builder(appCtx, InboxChannel.ID)
            .setSmallIcon(Icon.createWithResource(BuildConfig.APPLICATION_ID, R.drawable.ic_launcher))
            .setContentTitle(summary)
            .setContentText(visible.firstOrNull()?.let {
                val label = appLabel(sysCtx, it.pkg)
                if (it.title.isBlank()) label else "$label · ${it.title}"
            } ?: summary)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openLogsIntent(appCtx))
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setColor(0xFF0B57D0.toInt())
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setAllowSystemGeneratedContextualActions(false)
        }
        val built = builder.build()
        built.extras.putBoolean(EXTRA_MARKER, true)
        return built
    }

    private fun postSummary(nm: NotificationManager, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 30) {
            nm.notifyAsPackage(BuildConfig.APPLICATION_ID, TAG, ID, notification)
            return
        }
        val service = nms
        if (service != null) {
            try {
                Xp.callMethod(
                    service,
                    "enqueueNotificationInternal",
                    BuildConfig.APPLICATION_ID,
                    BuildConfig.APPLICATION_ID,
                    appUid(),
                    android.os.Process.myPid(),
                    TAG,
                    ID,
                    notification,
                    0,
                )
                return
            } catch (t: Throwable) {
                Xp.log("enqueue summary fallback", t)
            }
        }
        nm.notify(TAG, ID, notification)
    }

    private fun cancelSummary(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                nm.cancelAsPackage(BuildConfig.APPLICATION_ID, TAG, ID)
                return
            } catch (t: Throwable) {
                Xp.log("cancelAsPackage failed", t)
            }
        }
        nm.cancel(TAG, ID)
    }

    private fun ensureChannel(sysCtx: Context, appCtx: Context) {
        val channel = NotificationChannel(
            InboxChannel.ID,
            appCtx.getString(R.string.blocked_inbox_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appCtx.getString(R.string.blocked_inbox_channel_desc)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val service = nms
        if (service != null) {
            try {
                Xp.callMethod(
                    service,
                    "createNotificationChannel",
                    BuildConfig.APPLICATION_ID,
                    appUid(),
                    channel,
                    true,
                    false,
                )
                return
            } catch (_: Throwable) {
                // Fall through to NotificationManager.
            }
        }
        try {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        } catch (t: Throwable) {
            Xp.log("create channel failed", t)
        }
    }

    private fun undoIntent(ctx: Context, key: String): PendingIntent {
        val intent = Intent(ACTION_UNDO).putExtra(EXTRA_KEY, key)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, key.hashCode(), intent, flags)
    }

    private fun openLogsIntent(appCtx: Context): PendingIntent {
        val intent = Intent().setClassName(
            BuildConfig.APPLICATION_ID,
            MainActivity::class.java.name,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_LOGS, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(appCtx, 1, intent, flags)
    }

    private fun appLabel(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Throwable) {
            pkg.substringAfterLast('.')
        }
    }

    private fun appUid(): Int {
        val ctx = context ?: return android.os.Process.SYSTEM_UID
        return try {
            ctx.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid
        } catch (_: Throwable) {
            android.os.Process.SYSTEM_UID
        }
    }

    private fun nmsHandler(service: Any): Handler? {
        for (name in arrayOf("mWorkerHandler", "mHandler", "mMainHandler")) {
            try {
                val value = Xp.getField(service, name)
                if (value is Handler) return value
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun parseArgs(args: Array<Any?>): Parsed? {
        val nIndex = args.indexOfFirst { it is Notification }
        if (nIndex < 0) return null
        val id = args.getOrNull(nIndex - 1) as? Int ?: 0
        val tag = args.getOrNull(nIndex - 2) as? String
        var pkg: String? = null
        for (arg in args) {
            if (arg is String && arg.contains('.')) {
                pkg = arg
                break
            }
        }
        pkg = Xiaomi.resolvePackage(pkg, args[nIndex] as Notification).ifBlank { pkg }
        if (pkg.isNullOrBlank()) return null
        return Parsed(pkg = pkg, tag = tag, id = id)
    }

    private data class Parsed(val pkg: String, val tag: String?, val id: Int)

    private class BlockedItem(
        val key: String,
        val pkg: String,
        val title: String,
        val text: String,
        val method: Method?,
        val nms: Any?,
        val args: Array<Any?>,
    )

    companion object {
        const val EXTRA_MARKER = "moe.notice.filter.inbox"
        const val TAG = "blocked"
        const val ID = 2001
        const val ACTION_UNDO = "moe.notice.filter.UNDO_BLOCKED"
        const val EXTRA_KEY = "key"
        const val MAX_ITEMS = 20
        const val MAX_ROWS = 4
    }
}
