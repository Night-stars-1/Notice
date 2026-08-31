package moe.notice.filter.domain

/**
 * 与 ml/features.py 逐位一致的特征化。
 * 流程：转小写 -> 去掉所有空白、十进制数字和字母 'x'，
 * 然后对 UTF-16 码元取字符 n-gram（1..3），并用 FNV-1a 32 位哈希。
 * 之所以去掉空白、数字和 'x'，是因为训练语料会去掉正常短信中的空格，并把垃圾短信中的数字/姓名
 * 掩码为连续的 'x'；保留它们会让模型学到这些人工痕迹而不是周围的词语（见 ml/features.py）。
 */
object SpamFeatures {
    const val BUCKETS = 1 shl 18
    const val NGRAM_MIN = 1
    const val NGRAM_MAX = 3

    private const val FNV_OFFSET = 0x811C9DC5.toInt()
    private const val FNV_PRIME = 0x01000193

    // 需与 features.SPACE_CHARS 保持同步。
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

    /**
     * 与 [normalize] 相同的归一化，但额外返回「归一化后每个字符在原文中的下标」，供解释高亮使用。
     * 大小写按单字符转换（与 String.lowercase() 在极少数特殊字符上可能不同）。
     */
    fun normalizeWithMap(text: String): Pair<String, IntArray> {
        val out = StringBuilder(text.length)
        val map = IntArray(text.length)
        var n = 0
        for (i in text.indices) {
            val raw = Character.toLowerCase(text[i])
            if (raw in SPACE_CHARS || raw == 'x') continue
            if (Character.getType(raw) == Character.DECIMAL_DIGIT_NUMBER.toInt()) continue
            out.append(raw)
            map[n++] = i
        }
        return out.toString() to map.copyOf(n)
    }

    /** 对 [start, end) 区间内的 UTF-16 码元计算 FNV-1a，每个码元按（低字节, 高字节）顺序输入。 */
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
