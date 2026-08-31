package moe.notice.filter.domain

object RuleMatcher {
    /** 规则评估结果：[block] / [allow] 是终止本次评估的那条规则；[skipAi] 表示命中过「不进行智能识别」。 */
    data class Decision(
        val block: BlockRule? = null,
        val allow: BlockRule? = null,
        val skipAi: Boolean = false,
    ) {
        /** 决定日志里显示的规则：拦截或放行它的那条。 */
        val rule: BlockRule? get() = block ?: allow
    }

    /**
     * 按顺序评估规则：命中「拦截」或「放行」立即结束；命中「不进行智能识别」只做标记并继续，
     * 这样后面的拦截规则仍然生效。
     */
    fun evaluate(rules: List<BlockRule>, packageName: String, text: String): Decision {
        if (text.isEmpty() && rules.none { it.enabled && it.mode == MatchMode.ALL_CONTENT }) {
            return Decision()
        }
        val haystack = text.lowercase()
        var skipAi = false
        for (rule in rules) {
            if (!rule.enabled || !appliesTo(rule, packageName) || !matches(rule, text, haystack)) continue
            when (rule.action) {
                RuleAction.BLOCK -> return Decision(block = rule, skipAi = skipAi)
                RuleAction.ALLOW -> return Decision(allow = rule, skipAi = true)
                RuleAction.SKIP_AI -> skipAi = true
            }
        }
        return Decision(skipAi = skipAi)
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
