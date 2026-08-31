package moe.notice.filter.xposed

import android.content.ContentValues
import android.content.Context
import android.util.Log
import moe.notice.filter.provider.NotificationLogProvider

/**
 * 把模块运行日志镜像到应用侧，供「运行日志」页面查看。
 * 拿到 [Context] 之前（开机 hook 阶段）的日志先放在内存队列里，attach 后补发；
 * 之后的日志走 [LogSink] 的批量投递通道。
 */
internal object DebugLog {
    private const val MAX_PENDING = 500
    private val lock = Any()
    private val pending = ArrayDeque<ContentValues>()
    /** 由配置控制；配置加载前默认开启，以便保留开机阶段的日志。 */
    @Volatile var enabled: Boolean = true
    @Volatile private var context: Context? = null
    @Volatile private var sink: LogSink? = null

    fun attach(ctx: Context, logSink: LogSink) {
        if (context != null) return
        val backlog: List<ContentValues>
        synchronized(lock) {
            if (context != null) return
            context = ctx
            sink = logSink
            backlog = pending.toList()
            pending.clear()
        }
        for (values in backlog) logSink.submit(ctx, values, NotificationLogProvider.DEBUG_URI)
    }

    fun append(level: Int, message: String, error: Throwable?) {
        if (!enabled) return
        val values = ContentValues().apply {
            put(NotificationLogProvider.COL_TIMESTAMP, System.currentTimeMillis())
            put(NotificationLogProvider.COL_LEVEL, level)
            put(NotificationLogProvider.COL_MESSAGE, message)
            if (error != null) put(NotificationLogProvider.COL_TRACE, Log.getStackTraceString(error))
        }
        val ctx = context
        val logSink = sink
        if (ctx != null && logSink != null) {
            logSink.submit(ctx, values, NotificationLogProvider.DEBUG_URI)
        } else {
            synchronized(lock) {
                pending.addLast(values)
                while (pending.size > MAX_PENDING) pending.removeFirst()
            }
        }
    }
}
