package moe.notice.filter.domain

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream

/**
 * Sparse weight correction learned on-device and laid over the bundled [SpamModel].
 * Binary format (big-endian): "NSPD", int32 version=1, int32 buckets, int32 count,
 * then count × (int32 index, float32 value).
 */
class SpamDelta(
    val buckets: Int,
    val indices: IntArray,
    val values: FloatArray,
) {
    init {
        require(indices.size == values.size) { "indices ${indices.size} != values ${values.size}" }
    }

    val isEmpty: Boolean get() = indices.isEmpty()

    fun encode(): ByteArray {
        val bos = ByteArrayOutputStream(16 + indices.size * 8)
        DataOutputStream(bos).use { out ->
            out.write(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(buckets)
            out.writeInt(indices.size)
            for (i in indices.indices) {
                out.writeInt(indices[i])
                out.writeFloat(values[i])
            }
        }
        return bos.toByteArray()
    }

    companion object {
        /** Name of the libxposed remote file the app writes and system_server reads. */
        const val REMOTE_FILE = "spam_delta.bin"
        private const val VERSION = 1
        private val MAGIC = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte())

        fun empty(buckets: Int) = SpamDelta(buckets, IntArray(0), FloatArray(0))

        fun decode(input: InputStream): SpamDelta {
            val din = DataInputStream(input.buffered())
            val magic = ByteArray(4)
            din.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "bad magic" }
            val version = din.readInt()
            require(version == VERSION) { "unsupported version $version" }
            val buckets = din.readInt()
            val count = din.readInt()
            require(buckets > 0 && count in 0..buckets) { "bad header buckets=$buckets count=$count" }
            val indices = IntArray(count)
            val values = FloatArray(count)
            for (i in 0 until count) {
                indices[i] = din.readInt()
                values[i] = din.readFloat()
                require(indices[i] in 0 until buckets) { "index out of range ${indices[i]}" }
            }
            return SpamDelta(buckets, indices, values)
        }
    }
}
