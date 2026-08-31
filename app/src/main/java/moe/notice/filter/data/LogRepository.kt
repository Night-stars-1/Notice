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

class LogRepository private constructor(private val file: File) {
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
            val next = ArrayList<NotificationRecord>(MAX_ITEMS)
            next += record
            for (item in _items.value) {
                if (next.size >= MAX_ITEMS) break
                next += item
            }
            writeLocked(next)
            _items.value = next
        }
        return record
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
    )

    companion object {
        private const val MAX_ITEMS = 500
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
