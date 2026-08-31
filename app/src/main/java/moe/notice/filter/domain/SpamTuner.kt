package moe.notice.filter.domain

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 根据用户标注的样本，在冻结的基础模型之上拟合一个稀疏的 [SpamDelta]：
 * 从 Δ = 0 出发，用普通 SGD 最小化 Σ logloss(σ(z_base + Δ·x)) + l2/2·‖Δ‖²。
 * 过程是确定性的（固定顺序、固定轮数），因此相同的标注总会得到相同的 delta。
 */
object SpamTuner {
    data class Sample(val text: String, val spam: Boolean)

    private class Prepared(val z0: Float, val y: Float, val keys: IntArray, val x: FloatArray)

    fun fit(
        base: SpamModel,
        samples: List<Sample>,
        epochs: Int = 60,
        lr: Float = 1f,
        l2: Float = 0.005f,
    ): SpamDelta {
        val prepared = samples.mapNotNull { prepare(base, it) }
        if (prepared.isEmpty()) return SpamDelta.empty(base.buckets)

        val delta = HashMap<Int, Float>()
        repeat(epochs) {
            for (s in prepared) {
                var z = s.z0
                for (i in s.keys.indices) z += (delta[s.keys[i]] ?: 0f) * s.x[i]
                val g = sigmoid(z) - s.y
                for (i in s.keys.indices) {
                    val k = s.keys[i]
                    val current = delta[k] ?: 0f
                    delta[k] = current - lr * (g * s.x[i] + l2 * current)
                }
            }
        }
        val keys = delta.keys.filter { delta[it] != 0f }.sorted()
        return SpamDelta(
            buckets = base.buckets,
            indices = keys.toIntArray(),
            values = FloatArray(keys.size) { delta[keys[it]]!! },
        )
    }

    private fun prepare(base: SpamModel, sample: Sample): Prepared? {
        if (SpamFeatures.normalize(sample.text).length < SpamJudge.MIN_LENGTH) return null
        val counts = SpamFeatures.buckets(sample.text, base.ngramMin, base.ngramMax, base.buckets)
        if (counts.isEmpty()) return null
        var sq = 0.0
        for (c in counts.values) sq += c.toDouble() * c
        val norm = sqrt(sq)
        val keys = IntArray(counts.size)
        val x = FloatArray(counts.size)
        var z0 = base.bias
        var i = 0
        for ((k, c) in counts) {
            keys[i] = k
            x[i] = (c / norm).toFloat()
            z0 += base.weights[k] * x[i]
            i++
        }
        return Prepared(z0 = z0, y = if (sample.spam) 1f else 0f, keys = keys, x = x)
    }

    private fun sigmoid(z: Float): Float {
        val zd = z.toDouble()
        return if (zd >= 0) (1.0 / (1.0 + exp(-zd))).toFloat() else exp(zd).let { (it / (1.0 + it)).toFloat() }
    }
}
