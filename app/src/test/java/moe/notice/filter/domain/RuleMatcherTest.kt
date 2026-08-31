package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    private fun rule(id: String, keyword: String, action: RuleAction, pkg: List<String> = emptyList()) =
        BlockRule(id = id, name = id, keywords = listOf(keyword), action = action, packages = pkg)

    @Test
    fun blockRuleWins() {
        val d = RuleMatcher.evaluate(listOf(rule("b", "促销", RuleAction.BLOCK)), "a.b", "限时促销")
        assertEquals("b", d.block?.id)
        assertNull(d.allow)
    }

    @Test
    fun allowRuleStopsEvaluationAndSkipsAi() {
        val rules = listOf(rule("w", "银行", RuleAction.ALLOW), rule("b", "促销", RuleAction.BLOCK))
        val d = RuleMatcher.evaluate(rules, "a.b", "银行促销")
        assertNull(d.block)
        assertEquals("w", d.allow?.id)
        assertTrue(d.skipAi)
        assertEquals("w", d.rule?.id)
    }

    @Test
    fun skipAiKeepsMatchingLaterBlockRules() {
        val rules = listOf(rule("s", "群", RuleAction.SKIP_AI), rule("b", "促销", RuleAction.BLOCK))
        val blocked = RuleMatcher.evaluate(rules, "a.b", "群内促销")
        assertEquals("b", blocked.block?.id)
        assertTrue(blocked.skipAi)

        val passed = RuleMatcher.evaluate(rules, "a.b", "群聊消息")
        assertNull(passed.block)
        assertNull(passed.allow)
        assertTrue(passed.skipAi)
    }

    @Test
    fun noMatchMeansNothingSkipped() {
        val d = RuleMatcher.evaluate(listOf(rule("s", "群", RuleAction.SKIP_AI)), "a.b", "普通消息")
        assertFalse(d.skipAi)
        assertNull(d.rule)
    }

    @Test
    fun respectsPackageScope() {
        val rules = listOf(rule("b", "促销", RuleAction.BLOCK, pkg = listOf("com.x")))
        assertNull(RuleMatcher.evaluate(rules, "com.y", "促销").block)
        assertEquals("b", RuleMatcher.evaluate(rules, "com.x", "促销").block?.id)
    }
}
