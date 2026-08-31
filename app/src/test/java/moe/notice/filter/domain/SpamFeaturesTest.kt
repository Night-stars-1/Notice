package moe.notice.filter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFeaturesTest {
    @Test
    fun normalizeLowerDigitsSpaces() {
        assertEquals("helloworld", SpamFeatures.normalize("  Hello　World  123 "))
        assertEquals("ａｂｃ", SpamFeatures.normalize("ＡＢＣ１２３"))
        assertEquals("ab", SpamFeatures.normalize("a\t\n b"))
        assertEquals("", SpamFeatures.normalize("\u00A0\u3000"))
        assertEquals("bo", SpamFeatures.normalize("Xbox 360"))
        assertEquals("", SpamFeatures.normalize(""))
    }

    @Test
    fun fnv1a32KnownVectors() {
        assertEquals(0x811C9DC5.toInt(), SpamFeatures.fnv1a32("", 0, 0))
        // 单个 UTF-16 单元 "a" 对应字节 0x61 0x00；与按字节计算的参考实现对比。
        assertEquals(fnvOfBytes(byteArrayOf(0x61, 0x00)), SpamFeatures.fnv1a32("a", 0, 1))
        assertEquals(fnvOfBytes("foobar".toByteArray(Charsets.UTF_16LE)), SpamFeatures.fnv1a32("foobar", 0, 6))
    }

    @Test
    fun bucketsUnigramCounts() {
        val b = SpamFeatures.buckets("aa")
        assertEquals(listOf(1, 2), b.values.sorted())
        assertTrue(b.keys.all { it in 0 until SpamFeatures.BUCKETS })
    }

    @Test
    fun bucketsUseUtf16Units() {
        val b = SpamFeatures.buckets("😀")
        assertEquals(3, b.values.sum())
    }

    private fun fnvOfBytes(bytes: ByteArray): Int {
        var h = 0x811C9DC5.toInt()
        for (byte in bytes) {
            h = h xor (byte.toInt() and 0xFF)
            h *= 0x01000193
        }
        return h
    }
}
