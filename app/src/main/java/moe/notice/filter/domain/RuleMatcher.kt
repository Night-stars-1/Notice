package moe.notice.filter.domain

object RuleMatcher {
    fun firstMatch(rules: List<BlockRule>, packageName: String, text: String): BlockRule? {
        if (text.isEmpty() && rules.none { it.enabled && it.mode == MatchMode.ALL_CONTENT }) {
            return null
        }
        val haystack = text.lowercase()
        return rules.firstOrNull { rule ->
            rule.enabled && appliesTo(rule, packageName) && matches(rule, text, haystack)
        }
    }

    fun appliesTo(rule: BlockRule, packageName: String): Boolean {
        if (rule.packages.isEmpty()) return true
        val listed = packageName in rule.packages
        return if (rule.appListMode == AppListMode.BLACKLIST) !listed else listed
    }

    fun matches(rule: BlockRule, text: String, haystack: String = text.lowercase()): Boolean {
        return when (rule.mode) {
            MatchMode.CONTAINS_ANY ->
                rule.keywords.isNotEmpty() && rule.keywords.any { haystack.contains(it.lowercase()) }

            MatchMode.CONTAINS_ALL ->
                rule.keywords.isNotEmpty() && rule.keywords.all { haystack.contains(it.lowercase()) }

            MatchMode.NOT_CONTAINS_ANY ->
                rule.keywords.isNotEmpty() && rule.keywords.none { haystack.contains(it.lowercase()) }

            MatchMode.NOT_CONTAINS_ALL ->
                rule.keywords.isNotEmpty() && !rule.keywords.all { haystack.contains(it.lowercase()) }

            MatchMode.CONTAINS_A_NOT_B -> {
                val hasA = rule.keywords.any { haystack.contains(it.lowercase()) }
                val hasB = rule.excludeKeywords.any { haystack.contains(it.lowercase()) }
                rule.keywords.isNotEmpty() && hasA && !hasB
            }

            MatchMode.REGEX ->
                rule.compiled.any { regex ->
                    runCatching { regex.containsMatchIn(text) }.getOrDefault(false)
                }

            MatchMode.ALL_CONTENT -> true
        }
    }
}
