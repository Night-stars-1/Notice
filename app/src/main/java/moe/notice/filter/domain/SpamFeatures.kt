package moe.notice.filter.domain

/**
 * Featurisation shared bit-for-bit with ml/features.py.
 * Pipeline: lowercase -> drop all whitespace, decimal digits and the letter 'x',
 * then char n-grams (1..3) over UTF-16 code units hashed with FNV-1a 32-bit.
 * Whitespace, digits and 'x' are dropped because the training corpus strips spaces from ham and
 * masks digits/names in spam as runs of 'x'; keeping them would teach the model those artifacts
 * instead of the surrounding words (see ml/features.py).
 */
object SpamFeatures {
    const val BUCKETS = 1 shl 18
    const val NGRAM_MIN = 1
    const val NGRAM_MAX = 3

    private const val FNV_OFFSET = 0x811C9DC5.toInt()
    private const val FNV_PRIME = 0x01000193

    // Keep in sync with features.SPACE_CHARS.
    private val SPACE_CHARS: Set<Char> = (
        "\t\n\u000B\u000C\r\u001C\u001D\u001E\u001F \u0085\u00A0\u1680" +
            "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
            "\u2028\u2029\u202F\u205F\u3000"
        ).toSet()

    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (raw in text.lowercase()) {
            if (raw in SPACE_CHARS || raw == 'x') continue
            if (Character.getType(raw) == Character.DECIMAL_DIGIT_NUMBER.toInt()) continue
            out.append(raw)
        }
        return out.toString()
    }

    /** FNV-1a over the UTF-16 code units in [start, end), each fed as (low byte, high byte). */
    fun fnv1a32(text: CharSequence, start: Int, end: Int): Int {
        var h = FNV_OFFSET
        for (i in start until end) {
            val c = text[i].code
            h = h xor (c and 0xFF)
            h *= FNV_PRIME
            h = h xor ((c ushr 8) and 0xFF)
            h *= FNV_PRIME
        }
        return h
    }

    fun buckets(
        text: String,
        ngramMin: Int = NGRAM_MIN,
        ngramMax: Int = NGRAM_MAX,
        buckets: Int = BUCKETS,
    ): Map<Int, Int> {
        val s = normalize(text)
        val mask = buckets - 1
        val counts = HashMap<Int, Int>()
        for (n in ngramMin..ngramMax) {
            var start = 0
            while (start + n <= s.length) {
                val k = fnv1a32(s, start, start + n) and mask
                counts[k] = (counts[k] ?: 0) + 1
                start++
            }
        }
        return counts
    }
}
