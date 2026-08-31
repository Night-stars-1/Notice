package moe.notice.filter.domain

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 基于 [SpamFeatures] 哈希 n-gram 的逻辑回归垃圾短信评分器。
 * 二进制格式（大端序）："NSPM"、int32 version=1、int32 buckets、int32 ngramMin、
 * int32 ngramMax、float32 bias、float32 scale，然后是 `buckets` 个 int8 权重（w = q * scale）。
 */
class SpamModel(
    val buckets: Int,
    val ngramMin: Int,
    val ngramMax: Int,
    val bias: Float,
    val weights: FloatArray,
) {
    init {
        require(weights.size == buckets) { "weights ${weights.size} != buckets $buckets" }
        require(buckets > 0 && buckets and (buckets - 1) == 0) { "buckets must be a power of two" }
    }

    /** [text] 为垃圾信息的概率，取值范围 [0, 1]。 */
    fun score(text: String): Float {
        val counts = SpamFeatures.buckets(text, ngramMin, ngramMax, buckets)
        var z = bias
        if (counts.isNotEmpty()) {
            var sq = 0.0
            for (c in counts.values) sq += (c.toDouble() * c)
            val norm = sqrt(sq)
            for ((k, c) in counts) z += weights[k] * (c / norm).toFloat()
        }
        return sigmoid(z)
    }

    /** 将 [delta] 叠加到权重后得到的模型副本；桶数不匹配时忽略该 delta。 */
    fun withDelta(delta: SpamDelta): SpamModel {
        if (delta.buckets != buckets || delta.isEmpty) return this
        val merged = weights.copyOf()
        for (i in delta.indices.indices) merged[delta.indices[i]] += delta.values[i]
        return SpamModel(buckets, ngramMin, ngramMax, bias, merged)
    }

    companion object {
        const val RESOURCE = "model/spam_v1.bin"
        private const val VERSION = 1
        private val MAGIC = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 'M'.code.toByte())

        @Volatile private var bundledLoaded = false
        @Volatile private var bundledModel: SpamModel? = null

        fun load(input: InputStream): SpamModel {
            val din = DataInputStream(input.buffered())
            val magic = ByteArray(4)
            din.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "bad magic" }
            val version = din.readInt()
            require(version == VERSION) { "unsupported version $version" }
            val buckets = din.readInt()
            val ngramMin = din.readInt()
            val ngramMax = din.readInt()
            val bias = din.readFloat()
            val scale = din.readFloat()
            require(buckets in 1..(1 shl 24)) { "bad bucket count $buckets" }
            val q = ByteArray(buckets)
            din.readFully(q)
            val weights = FloatArray(buckets) { q[it] * scale }
            return SpamModel(buckets, ngramMin, ngramMax, bias, weights)
        }

        /** 打包在 APK 中的模型，只加载一次；缺失或损坏时为 null。 */
        fun bundled(): SpamModel? {
            if (bundledLoaded) return bundledModel
            synchronized(this) {
                if (bundledLoaded) return bundledModel
                bundledModel = runCatching {
                    SpamModel::class.java.classLoader?.getResourceAsStream(RESOURCE)?.use { load(it) }
                }.getOrNull()
                bundledLoaded = true
                return bundledModel
            }
        }

        private fun sigmoid(z: Float): Float {
            val zd = z.toDouble()
            return if (zd >= 0) {
                (1.0 / (1.0 + exp(-zd))).toFloat()
            } else {
                val e = exp(zd)
                (e / (1.0 + e)).toFloat()
            }
        }
    }
}
