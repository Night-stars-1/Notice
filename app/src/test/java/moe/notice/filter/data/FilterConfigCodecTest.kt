package moe.notice.filter.data

import moe.notice.filter.domain.BlockRule
import moe.notice.filter.domain.FilterConfig
import moe.notice.filter.domain.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterConfigCodecTest {
    @Test
    fun roundTripsSpamFields() {
        val config = FilterConfig(enabled = true, spamEnabled = true, spamThreshold = 0.73f)
        val decoded = FilterConfigCodec.decode(FilterConfigCodec.encode(config))
        assertTrue(decoded.spamEnabled)
        assertEquals(0.73f, decoded.spamThreshold, 1e-6f)
    }

    @Test
    fun legacyJsonWithoutSpamFieldsUsesDefaults() {
        val decoded = FilterConfigCodec.decode("""{"enabled":true,"logEnabled":false,"rules":[]}""")
        assertFalse(decoded.spamEnabled)
        assertEquals(0.9f, decoded.spamThreshold, 1e-6f)
    }

    @Test
    fun roundTripsExclusionsAndDeltaVersion() {
        val config = FilterConfig(spamExcludedPackages = listOf("com.a", "com.b"), spamDeltaVersion = 1234L)
        val decoded = FilterConfigCodec.decode(FilterConfigCodec.encode(config))
        assertEquals(listOf("com.a", "com.b"), decoded.spamExcludedPackages)
        assertEquals(1234L, decoded.spamDeltaVersion)
        val legacy = FilterConfigCodec.decode("""{"enabled":true}""")
        assertTrue(legacy.spamExcludedPackages.isEmpty())
        assertEquals(0L, legacy.spamDeltaVersion)
    }

    @Test
    fun roundTripsDebugLogSwitch() {
        val off = FilterConfigCodec.decode(FilterConfigCodec.encode(FilterConfig(debugLogEnabled = false)))
        assertFalse(off.debugLogEnabled)
        assertTrue(FilterConfigCodec.decode("""{"enabled":true}""").debugLogEnabled)
        val judgeOn = FilterConfigCodec.decode(FilterConfigCodec.encode(FilterConfig(judgeLogEnabled = true)))
        assertTrue(judgeOn.judgeLogEnabled)
        assertFalse(FilterConfigCodec.decode("""{"enabled":true}""").judgeLogEnabled)
    }

    @Test
    fun roundTripsRuleAction() {
        val config = FilterConfig(
            rules = listOf(
                BlockRule(id = "a", keywords = listOf("x"), action = RuleAction.ALLOW),
                BlockRule(id = "s", keywords = listOf("y"), action = RuleAction.SKIP_AI),
                BlockRule(id = "b", keywords = listOf("z")),
            ),
        )
        val decoded = FilterConfigCodec.decode(FilterConfigCodec.encode(config))
        assertEquals(listOf(RuleAction.ALLOW, RuleAction.SKIP_AI, RuleAction.BLOCK), decoded.rules.map { it.action })
        val legacy = FilterConfigCodec.decode("""{"rules":[{"id":"old","keywords":["k"]}]}""")
        assertEquals(RuleAction.BLOCK, legacy.rules.single().action)
    }

    @Test
    fun clampsThresholdIntoRange() {
        val decoded = FilterConfigCodec.decode("""{"spamThreshold":7}""")
        assertEquals(0.99f, decoded.spamThreshold, 1e-6f)
        val low = FilterConfigCodec.decode("""{"spamThreshold":-1}""")
        assertEquals(0.5f, low.spamThreshold, 1e-6f)
    }
}
