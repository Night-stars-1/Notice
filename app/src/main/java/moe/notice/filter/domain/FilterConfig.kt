package moe.notice.filter.domain

data class FilterConfig(
    val enabled: Boolean = false,
    val logEnabled: Boolean = true,
    val rules: List<BlockRule> = emptyList(),
    val spamEnabled: Boolean = false,
    val spamThreshold: Float = DEFAULT_SPAM_THRESHOLD,
    /** 垃圾短信模型从不评分的包名。 */
    val spamExcludedPackages: List<String> = emptyList(),
    /** 最近一次写入远程文件的设备端微调 delta 的时间戳；0 表示没有。 */
    val spamDeltaVersion: Long = 0L,
) {
    companion object {
        const val DEFAULT_SPAM_THRESHOLD = 0.9f
        const val MIN_SPAM_THRESHOLD = 0.5f
        const val MAX_SPAM_THRESHOLD = 0.99f
    }
}
