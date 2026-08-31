package moe.notice.filter.domain

import java.util.UUID

data class BlockRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val mode: MatchMode = MatchMode.CONTAINS_ANY,
    val keywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
    val appListMode: AppListMode = AppListMode.WHITELIST,
    val compiled: List<Regex> = emptyList(),
) {
    val appliesToAllApps: Boolean get() = packages.isEmpty()

    fun withCompiledRegex(): BlockRule {
        if (mode != MatchMode.REGEX) return copy(compiled = emptyList())
        return copy(
            compiled = keywords.mapNotNull { pattern ->
                runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
            },
        )
    }
}
