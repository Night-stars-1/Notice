package moe.notice.filter.data

import moe.notice.filter.domain.FilterConfig
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
    fun clampsThresholdIntoRange() {
        val decoded = FilterConfigCodec.decode("""{"spamThreshold":7}""")
        assertEquals(0.99f, decoded.spamThreshold, 1e-6f)
        val low = FilterConfigCodec.decode("""{"spamThreshold":-1}""")
        assertEquals(0.5f, low.spamThreshold, 1e-6f)
    }
}
