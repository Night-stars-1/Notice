package moe.notice.filter.data

/** KeywordFilter 输出的 judge 判定行的结构化视图；格式不符时 [parse] 返回 null。 */
data class JudgeLine(
    val pkg: String,
    val blocked: Boolean,
    val ruleName: String?,
    val score: Float?,
    val protected: Boolean,
    val text: String,
    /** 命中了「不进行智能识别」或「放行」规则，模型没有打分。 */
    val aiSkipped: Boolean = false,
) {
    companion object {
        // 注意：Android 使用 ICU 正则，`}` 必须转义（JVM 不强制，单测抓不到）。
        private val PATTERN = Regex(
            """^judge enabled=\S+ spamEnabled=\S+ rules=\{.*\} pkg=(\S+) result=(allow|block)(?::(.*?))?(?: spam=([0-9.]+)(\(protected\))?)?( ai=skipped)? text=(.*)$""",
            RegexOption.DOT_MATCHES_ALL,
        )

        fun parse(message: String): JudgeLine? {
            if (!message.startsWith("judge ")) return null
            val m = PATTERN.find(message) ?: return null
            val result = m.groupValues[2]
            return JudgeLine(
                pkg = m.groupValues[1],
                blocked = result == "block",
                ruleName = m.groupValues[3].takeIf { it.isNotBlank() },
                score = m.groupValues[4].toFloatOrNull(),
                protected = m.groupValues[5].isNotEmpty(),
                text = m.groupValues[7].trim(),
                aiSkipped = m.groupValues[6].isNotEmpty(),
            )
        }
    }
}
