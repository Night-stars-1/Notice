package moe.notice.filter.data

import moe.notice.filter.domain.NotificationDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDetailsCodecTest {
    @Test
    fun roundTripsSpamScore() {
        val details = NotificationDetails(spamScore = 0.93f, spamProtected = true)
        val decoded = NotificationDetailsCodec.fromJson(NotificationDetailsCodec.toJson(details))
        assertEquals(0.93f, decoded.spamScore!!, 1e-6f)
        assertTrue(decoded.spamProtected)
    }

    @Test
    fun missingScoreDecodesAsNull() {
        val decoded = NotificationDetailsCodec.fromJson(NotificationDetailsCodec.toJson(NotificationDetails()))
        assertNull(decoded.spamScore)
        assertFalse(decoded.spamProtected)
        assertNull(NotificationDetailsCodec.fromJson("""{"ticker":"x"}""").spamScore)
    }
}
