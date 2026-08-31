package moe.notice.filter.domain

data class FilterConfig(
    val enabled: Boolean = false,
    val logEnabled: Boolean = true,
    val rules: List<BlockRule> = emptyList(),
)
