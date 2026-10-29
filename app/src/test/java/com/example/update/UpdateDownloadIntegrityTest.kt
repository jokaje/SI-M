package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Guards the download pipeline: exact size, SHA-256 match, oversize and truncation handling.
 * Runs against in-memory streams so no network or GitHub release is required.
 */
class UpdateDownloadIntegrityTest {

    private fun payload(size: Int, seed: Int = 7): ByteArray =
        ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun copy(
        input: InputStream,
        expectedSize: Long,
        expectedSha: String,
        limit: Long = MAX_APK,
        onProgress: (Int) -> Unit = {},
    ): Pair<Long, ByteArray> {
        val out = ByteArrayOutputStream()
        val total = copyVerified(input, out, expectedSize, expectedSha, limit, onProgress)
        return total to out.toByteArray()
    }

    @Test
    fun `copies matching payload and reports progress`() {
        val data = payload(70_000)
        val progress = mutableListOf<Int>()
        val (total, written) = copy(ByteArrayInputStream(data), data.size.toLong(), sha(data)) { progress.add(it) }
        assertEquals(data.size.toLong(), total)
        assertEquals(data.toList(), written.toList())
        assertEquals(100, progress.last())
        // progress only moves forward and never exceeds 100
        assertEquals(progress.sorted(), progress)
        assertEquals(true, progress.all { it in 0..100 })
    }

    @Test
    fun `rejects payload with wrong checksum`() {
        val data = payload(4096)
        assertThrows(IllegalArgumentException::class.java) {
            copy(ByteArrayInputStream(data), data.size.toLong(), "b".repeat(64))
        }
    }

    @Test
    fun `rejects truncated payload`() {
        val data = payload(4096)
        val truncated = data.copyOf(2048)
        val error = assertThrows(IllegalArgumentException::class.java) {
            copy(ByteArrayInputStream(truncated), data.size.toLong(), sha(data))
        }
        assertEquals(true, error.message!!.contains("unvollständig"))
    }

    @Test
    fun `rejects payload larger than announced`() {
        val data = payload(4096)
        assertThrows(IllegalArgumentException::class.java) {
            copy(ByteArrayInputStream(data), 2048L, sha(data))
        }
    }

    @Test
    fun `rejects implausible expected size and exceeded hard limit`() {
        val data = payload(100)
        assertThrows(IllegalArgumentException::class.java) {
            copy(ByteArrayInputStream(data), 0L, sha(data))
        }
        assertThrows(IllegalArgumentException::class.java) {
            copy(ByteArrayInputStream(data), data.size.toLong(), sha(data), limit = 50L)
        }
    }

    @Test
    fun `honours cancellation callback`() {
        val data = payload(1_000_000)
        var chunks = 0
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) {
            copyVerified(
                input = ByteArrayInputStream(data),
                output = out,
                expectedSize = data.size.toLong(),
                expectedSha256 = sha(data),
                limit = MAX_APK,
                onProgress = {},
                onChunk = { if (++chunks > 2) throw IllegalStateException("cancelled") },
            )
        }
    }
}
