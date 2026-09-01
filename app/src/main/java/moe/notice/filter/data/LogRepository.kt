package moe.notice.filter.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.notice.filter.domain.NotificationDetails
import moe.notice.filter.domain.NotificationRecord
import org.json.JSONArray
import org.json.JSONObject

class LogRepository internal constructor(private val file: File) {
    private val lock = Any()
    private val _items = MutableStateFlow(readLocked())
    val items: StateFlow<List<NotificationRecord>> = _items.asStateFlow()

    fun add(
        packageName: String,
        title: String,
        text: String,
        timestamp: Long,
        blocked: Boolean,
        ruleId: String?,
        ruleName: String?,
        details: NotificationDetails = NotificationDetails(),
    ): NotificationRecord {
        val record = NotificationRecord(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            title = title,
            text = text,
            timestamp = timestamp,
            blocked = blocked,
            ruleId = ruleId,
            ruleName = ruleName,
            details = details,
        )
        synchronized(lock) {
            val current = _items.value
            val mergeIndex = if (isMergeable(record)) current.indexOfFirst { sameNotification(it, record) } else -1
            val next: List<NotificationRecord>
            val stored: NotificationRecord
            if (mergeIndex >= 0) {
                // 同一条活动通知的进度/持续更新会原地合并进已有的那一行。
                val previous = current[mergeIndex]
                stored = record.copy(id = previous.id, updateCount = previous.updateCount + 1)
                next = current.toMutableList().also { it[mergeIndex] = stored }
            } else {
                stored = record
                val list = ArrayList<NotificationRecord>(MAX_ITEMS)
                list += record
                for (item in current) {
                    if (list.size >= MAX_ITEMS) break
                    list += item
                }
                next = list
            }
            writeLocked(next)
            _items.value = next
            return stored
        }
    }

    /** 只合并进度条 / 持续通知；聊天式更新仍各占一行。 */
    private fun isMergeable(record: NotificationRecord): Boolean =
        hasProgressBar(record.details.progress) || record.details.flags and FLAG_ONGOING_EVENT != 0

    /**
     * 形如 "current / max" 或不确定进度条时为 true。单独的数字（例如 "0"）是钩子过去针对
     * NotificationCompat 给每条通知（包括聊天消息）都附加的 EXTRA_PROGRESS=0 / MAX=0 组合所输出的值，
     * 因此不能算作进度条。
     */
    private fun hasProgressBar(progress: String): Boolean =
        progress.contains('/') || progress == INDETERMINATE_PROGRESS

    private fun sameNotification(existing: NotificationRecord, incoming: NotificationRecord): Boolean =
        existing.packageName == incoming.packageName &&
            existing.details.notificationId == incoming.details.notificationId &&
            existing.details.tag == incoming.details.tag &&
            incoming.timestamp - existing.timestamp in 0..MERGE_WINDOW_MS

    /** 重新评分后写回分数；找不到记录时不做任何事。 */
    fun updateSpamScore(id: String, score: Float?, protected: Boolean) {
        synchronized(lock) {
            val current = _items.value
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return
            val old = current[index]
            val next = current.toMutableList().also {
                it[index] = old.copy(details = old.details.copy(spamScore = score, spamProtected = protected))
            }
            writeLocked(next)
            _items.value = next
        }
    }

    fun clear() {
        synchronized(lock) {
            writeLocked(emptyList())
            _items.value = emptyList()
        }
    }

    fun reload() {
        synchronized(lock) {
            _items.value = readLocked()
        }
    }

    private fun readLocked(): List<NotificationRecord> {
        if (!file.exists()) return emptyList()
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<NotificationRecord>(array.length())
            for (i in 0 until array.length()) {
                out += decode(array.getJSONObject(i))
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun writeLocked(items: List<NotificationRecord>) {
        val array = JSONArray()
        for (item in items) array.put(encode(item))
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    private fun encode(item: NotificationRecord): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("packageName", item.packageName)
        put("title", item.title)
        put("text", item.text)
        put("timestamp", item.timestamp)
        put("blocked", item.blocked)
        put("ruleId", item.ruleId)
        put("ruleName", item.ruleName)
        put("details", NotificationDetailsCodec.toJson(item.details))
        put("updateCount", item.updateCount)
    }

    private fun decode(obj: JSONObject): NotificationRecord = NotificationRecord(
        id = obj.optString("id"),
        packageName = obj.optString("packageName"),
        title = obj.optString("title"),
        text = obj.optString("text"),
        timestamp = obj.optLong("timestamp"),
        blocked = obj.optBoolean("blocked"),
        ruleId = obj.optString("ruleId").ifBlank { null },
        ruleName = obj.optString("ruleName").ifBlank { null },
        details = NotificationDetailsCodec.fromJson(obj.optString("details")),
        updateCount = obj.optInt("updateCount", 0),
    )

    companion object {
        private const val MAX_ITEMS = 500
        private const val MERGE_WINDOW_MS = 10 * 60 * 1000L
        private const val INDETERMINATE_PROGRESS = "不确定进度" // 必须与 NotificationCapture.progress() 一致
        private const val FLAG_ONGOING_EVENT = 0x00000002 // Notification.FLAG_ONGOING_EVENT（避免测试依赖 android.jar）
        private const val FILE_NAME = "notification_log.json"

        @Volatile
        private var instance: LogRepository? = null

        fun get(context: Context): LogRepository {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: LogRepository(File(context.applicationContext.filesDir, FILE_NAME)).also {
                    instance = it
                }
            }
        }
    }
}
