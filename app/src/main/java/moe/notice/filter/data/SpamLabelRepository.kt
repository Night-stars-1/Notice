package moe.notice.filter.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.notice.filter.domain.NotificationRecord
import org.json.JSONArray
import org.json.JSONObject

data class SpamLabel(
    val recordId: String,
    val packageName: String,
    val text: String,
    val spam: Boolean,
    val timestamp: Long,
)

/** 用户为已记录通知提供的垃圾/正常标注；即设备端微调的训练集。 */
class SpamLabelRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 上次成功下发的微调量所基于的内置模型指纹；0 表示尚未微调。 */
    var tunedModelFingerprint: Long
        get() = prefs.getLong(KEY_MODEL_FINGERPRINT, 0L)
        set(value) = prefs.edit().putLong(KEY_MODEL_FINGERPRINT, value).apply()
    private val lock = Any()
    private val _labels = MutableStateFlow(readLocked())
    val labels: StateFlow<Map<String, SpamLabel>> = _labels.asStateFlow()

    fun set(record: NotificationRecord, spam: Boolean) {
        val label = SpamLabel(
            recordId = record.id,
            packageName = record.packageName,
            text = trainingText(record),
            spam = spam,
            timestamp = System.currentTimeMillis(),
        )
        update { it[record.id] = label }
    }

    fun remove(recordId: String) = update { it.remove(recordId) }

    fun clear() = update { it.clear() }

    private fun update(block: (MutableMap<String, SpamLabel>) -> Unit) {
        synchronized(lock) {
            val next = LinkedHashMap(_labels.value)
            block(next)
            writeLocked(next)
            _labels.value = next
        }
    }

    private fun readLocked(): Map<String, SpamLabel> {
        if (!file.exists()) return emptyMap()
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            val out = LinkedHashMap<String, SpamLabel>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("recordId")
                if (id.isBlank()) continue
                out[id] = SpamLabel(
                    recordId = id,
                    packageName = obj.optString("packageName"),
                    text = obj.optString("text"),
                    spam = obj.optBoolean("spam"),
                    timestamp = obj.optLong("timestamp"),
                )
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun writeLocked(items: Map<String, SpamLabel>) {
        val array = JSONArray()
        for (item in items.values) {
            array.put(
                JSONObject().apply {
                    put("recordId", item.recordId)
                    put("packageName", item.packageName)
                    put("text", item.text)
                    put("spam", item.spam)
                    put("timestamp", item.timestamp)
                },
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(array.toString())
    }

    companion object {
        private const val FILE_NAME = "spam_labels.json"
        private const val PREFS_NAME = "spam_tuning"
        private const val KEY_MODEL_FINGERPRINT = "model_fingerprint"

        /** 近似 system_server 用于评分的合并文本（标题 + 正文）。 */
        fun trainingText(record: NotificationRecord): String =
            listOf(record.title, record.text).filter { it.isNotBlank() }.joinToString("\n")
    }
}
