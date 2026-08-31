package moe.notice.filter.domain

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 解释一条文本的骚扰分数：模型是线性的，把每个字符 n-gram 的贡献（权重 × 特征值）
 * 平摊到它覆盖的字符上，再把连续同号的字符合并成片段，就得到"哪些词把分数推高 / 拉低"。
 */
object SpamExplainer {
    /** [range] 是原文中的字符区间（闭区间），[contribution] 为 logit 贡献，正数推向骚扰。 */
    data class Term(val text: String, val contribution: Float, val range: IntRange)

    data class Explanation(
        val score: Float,
        /** 推高分数的片段，按贡献从大到小。 */
        val positives: List<Term>,
        /** 拉低分数的片段，按贡献绝对值从大到小。 */
        val negatives: List<Term>,
    )

    fun explain(model: SpamModel, text: String, top: Int = 5): Explanation {
        val (normalized, map) = SpamFeatures.normalizeWithMap(text)
        val score = model.score(text)
        if (normalized.isEmpty()) return Explanation(score, emptyList(), emptyList())

        val mask = model.buckets - 1
        val counts = HashMap<Int, Int>()
        for (n in model.ngramMin..model.ngramMax) {
            var start = 0
            while (start + n <= normalized.length) {
                val k = SpamFeatures.fnv1a32(normalized, start, start + n) and mask
                counts[k] = (counts[k] ?: 0) + 1
                start++
            }
        }
        var sq = 0.0
        for (c in counts.values) sq += c.toDouble() * c
        val norm = sqrt(sq).toFloat()

        // 每个 n-gram 出现一次贡献 w / norm；平摊到它覆盖的 n 个字符上。
        val perChar = FloatArray(normalized.length)
        for (n in model.ngramMin..model.ngramMax) {
            var start = 0
            while (start + n <= normalized.length) {
                val k = SpamFeatures.fnv1a32(normalized, start, start + n) and mask
                val share = model.weights[k] / norm / n
                for (i in start until start + n) perChar[i] += share
                start++
            }
        }

        val positives = runs(normalized, map, perChar, positive = true)
        val negatives = runs(normalized, map, perChar, positive = false)
        return Explanation(
            score = score,
            positives = positives.sortedByDescending { it.contribution }.take(top),
            negatives = negatives.sortedBy { it.contribution }.take(top),
        )
    }

    /** 分数对应的 logit（便于和贡献之和对照）。 */
    fun logit(score: Float): Float {
        val p = score.toDouble().coerceIn(1e-6, 1 - 1e-6)
        return ln(p / (1 - p)).toFloat()
    }

    private fun runs(normalized: String, map: IntArray, perChar: FloatArray, positive: Boolean): List<Term> {
        val out = ArrayList<Term>()
        var i = 0
        while (i < normalized.length) {
            val hit = if (positive) perChar[i] > EPS else perChar[i] < -EPS
            if (!hit) {
                i++
                continue
            }
            var j = i
            var sum = 0f
            while (j < normalized.length && (if (positive) perChar[j] > EPS else perChar[j] < -EPS) && j - i < MAX_RUN) {
                sum += perChar[j]
                j++
            }
            if (abs(sum) >= MIN_TERM) {
                out += Term(text = normalized.substring(i, j), contribution = sum, range = map[i]..map[j - 1])
            }
            i = j
        }
        return out
    }

    private const val EPS = 1e-4f
    private const val MIN_TERM = 0.05f
    private const val MAX_RUN = 12
}
