package moe.notice.filter.domain

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class SpamModelTest {
    @Test
    fun loadsHandBuiltBinary() {
        val bytes = ByteArrayOutputStream().also { bos ->
            DataOutputStream(bos).apply {
                write("NSPM".toByteArray(Charsets.US_ASCII))
                writeInt(1)
                writeInt(8)
                writeInt(1)
                writeInt(1)
                writeFloat(0.5f)
                writeFloat(0.01f)
                write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            }
        }.toByteArray()
        val m = SpamModel.load(bytes.inputStream())
        assertEquals(8, m.buckets)
        assertEquals(1, m.ngramMin)
        assertEquals(1, m.ngramMax)
        assertEquals(0.5f, m.bias, 0f)
        assertEquals(sigmoid(0.5f), m.score("anything"), 1e-6f)
        assertEquals(sigmoid(0.5f), m.score(""), 1e-6f)
    }

    @Test
    fun fingerprintFollowsFileContent() {
        fun bytes(bias: Float) = ByteArrayOutputStream().also { bos ->
            DataOutputStream(bos).apply {
                write("NSPM".toByteArray(Charsets.US_ASCII)); writeInt(1); writeInt(8); writeInt(1); writeInt(1)
                writeFloat(bias); writeFloat(0.01f); write(ByteArray(8))
            }
        }.toByteArray()
        val a = SpamModel.load(bytes(0.5f).inputStream())
        val b = SpamModel.load(bytes(0.5f).inputStream())
        val c = SpamModel.load(bytes(0.25f).inputStream())
        assertEquals(a.fingerprint, b.fingerprint)
        assertTrue(a.fingerprint != c.fingerprint)
        assertTrue(a.fingerprint != 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadMagic() {
        SpamModel.load(ByteArray(28).inputStream())
    }

    @Test
    fun bundledModelMatchesPythonParity() {
        val model = SpamModel.bundled()
        assertNotNull("model/spam_v1.bin missing from resources", model)
        val json = javaClass.classLoader!!.getResourceAsStream("model/parity.json")!!
            .bufferedReader().readText()
        val rows = JSONArray(json)
        assertTrue(rows.length() > 5)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val text = row.getString("text")
            val expected = row.getDouble("score").toFloat()
            assertEquals("row $i: $text", expected, model!!.score(text), 1e-3f)
        }
    }

    private fun sigmoid(z: Float): Float = (1.0 / (1.0 + Math.exp(-z.toDouble()))).toFloat()
}
