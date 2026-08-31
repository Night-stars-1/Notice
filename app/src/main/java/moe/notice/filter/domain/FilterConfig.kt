package moe.notice.filter.domain

data class FilterConfig(
    val enabled: Boolean = false,
    val logEnabled: Boolean = true,
    val rules: List<BlockRule> = emptyList(),
    val spamEnabled: Boolean = false,
    val spamThreshold: Float = DEFAULT_SPAM_THRESHOLD,
    /** Packages the spam model never scores. */
    val spamExcludedPackages: List<String> = emptyList(),
    /** Timestamp of the last on-device tuning delta written to the remote file; 0 = none. */
    val spamDeltaVersion: Long = 0L,
) {
    companion object {
        const val DEFAULT_SPAM_THRESHOLD = 0.9f
        const val MIN_SPAM_THRESHOLD = 0.5f
        const val MAX_SPAM_THRESHOLD = 0.99f
    }
}
