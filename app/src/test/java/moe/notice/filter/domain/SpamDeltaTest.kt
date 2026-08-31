package moe.notice.filter.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamDeltaTest {
    private fun model(buckets: Int, bias: Float = 0f) =
        SpamModel(buckets = buckets, ngramMin = 1, ngramMax = 1, bias = bias, weights = FloatArray(buckets))

    @Test
    fun roundTrips() {
        val delta = SpamDelta(buckets = 16, indices = intArrayOf(3, 9), values = floatArrayOf(0.5f, -1.25f))
        val decoded = SpamDelta.decode(delta.encode().inputStream())
        assertEquals(16, decoded.buckets)
        assertArrayEquals(intArrayOf(3, 9), decoded.indices)
        assertArrayEquals(floatArrayOf(0.5f, -1.25f), decoded.values, 0f)
    }

    @Test
    fun emptyDeltaRoundTrips() {
        val decoded = SpamDelta.decode(SpamDelta.empty(16).encode().inputStream())
        assertTrue(decoded.isEmpty)
        assertEquals(16, decoded.buckets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadMagic() {
        SpamDelta.decode(ByteArray(16).inputStream())
    }

    @Test
    fun overlayAddsOnlyListedWeights() {
        val base = model(16, bias = 0.25f)
        val tuned = base.withDelta(SpamDelta(16, intArrayOf(3, 9), floatArrayOf(0.5f, -1.25f)))
        assertEquals(0.5f, tuned.weights[3], 0f)
        assertEquals(-1.25f, tuned.weights[9], 0f)
        assertEquals(0f, tuned.weights[4], 0f)
        assertEquals(0.25f, tuned.bias, 0f)
        assertEquals(0f, base.weights[3], 0f) // base untouched
    }

    @Test
    fun overlayIgnoresBucketMismatch() {
        val base = model(16)
        val tuned = base.withDelta(SpamDelta(32, intArrayOf(3), floatArrayOf(9f)))
        assertEquals(0f, tuned.weights[3], 0f)
    }
}
