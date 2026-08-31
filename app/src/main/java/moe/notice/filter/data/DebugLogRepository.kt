package moe.notice.filter.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugLine(
    val timestamp: Long,
    val level: Int,
    val message: String,
    val trace: String = "",
) {
    val isError: Boolean get() = level >= LEVEL_ERROR
    /** 每条通知都会产生一行 judge 判定日志，筛选时可以隐藏。 */
    val isJudge: Boolean get() = message.startsWith("judge ")

    companion object {
        const val LEVEL_ERROR = 6 // android.util.Log.ERROR（避免测试依赖 android.jar）
    }
}

/** 模块运行日志：来自 system_server 的镜像，按行追加到本地文件，只保留最近 [MAX_LINES] 行。 */
class DebugLogRepository internal constructor(private val file: File) {
    private val lock = Any()
    private val _items = MutableStateFlow(readLocked())
    /** 最新的在最前。 */
    val items: StateFlow<List<DebugLine>> = _items.asStateFlow()

    fun append(timestamp: Long, level: Int, message: String, trace: String = "") {
        val line = DebugLine(timestamp, level, message, trace)
        synchronized(lock) {
            val next = ArrayList<DebugLine>(_items.value.size + 1)
            next += line
            next += _items.value
            if (next.size > MAX_LINES + TRIM_SLACK) {
                val trimmed = next.subList(0, MAX_LINES).toList()
                writeAllLocked(trimmed)
                _items.value = trimmed
            } else {
                file.parentFile?.mkdirs()
                file.appendText(encode(line) + "\n")
                _items.value = next
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeText("")
            _items.value = emptyList()
        }
    }

    private fun readLocked(): List<DebugLine> {
        if (!file.exists()) return emptyList()
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        val out = ArrayList<DebugLine>(lines.size)
        for (raw in lines.asReversed()) decode(raw)?.let { out += it }
        return out
    }

    private fun writeAllLocked(newestFirst: List<DebugLine>) {
        file.parentFile?.mkdirs()
        file.writeText(newestFirst.asReversed().joinToString("\n", postfix = "\n") { encode(it) })
    }

    private fun encode(line: DebugLine): String =
        listOf(line.timestamp.toString(), line.level.toString(), escape(line.message), escape(line.trace))
            .joinToString("\t")

    private fun decode(raw: String): DebugLine? {
        val parts = raw.split('\t')
        if (parts.size < 3) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val level = parts[1].toIntOrNull() ?: return null
        return DebugLine(ts, level, unescape(parts[2]), if (parts.size > 3) unescape(parts[3]) else "")
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(s[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    companion object {
        const val MAX_LINES = 2000
        const val TRIM_SLACK = 200
        private const val FILE_NAME = "debug_log.txt"

        @Volatile
        private var instance: DebugLogRepository? = null

        fun get(context: Context): DebugLogRepository {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: DebugLogRepository(File(context.applicationContext.filesDir, FILE_NAME)).also {
                    instance = it
                }
            }
        }
    }
}
