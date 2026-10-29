package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

/**
 * Tests for URL pinning and manifest validation. Robolectric supplies the real
 * `org.json` implementation, which the plain JVM stub does not provide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class UpdateManifestTest {

    private val installed = 7L
    private val sdk = 35
    private val pkg = "com.aistudio.sim.aiorb"
    private val sha = "a".repeat(64)

    private fun manifest(
        schemaVersion: Int = 1,
        packageName: String = pkg,
        versionCode: Long = 8,
        versionName: String = "1.2.2",
        apkUrl: String = APK_ASSET_URL,
        sha256: String = sha,
        sizeBytes: Long = 12_345_678,
        minSdk: Int = 26,
        releaseNotes: String = "Fehlerbehebungen",
        extra: String = "",
    ) = """
        {"schemaVersion":$schemaVersion,"packageName":"$packageName","versionCode":$versionCode,
         "versionName":"$versionName","apkUrl":"$apkUrl","sha256":"$sha256","sizeBytes":$sizeBytes,
         "minSdk":$minSdk,"releaseNotes":"$releaseNotes"$extra}
    """.trimIndent().toByteArray()

    private fun parse(bytes: ByteArray) = parseManifest(bytes, installed, sdk, pkg)

    @Test
    fun `accepts well formed manifest from canonical asset url`() {
        val parsed = parse(manifest())
        assertEquals(8L, parsed.versionCode)
        assertEquals("1.2.2", parsed.versionName)
        assertEquals(sha, parsed.sha256)
        assertEquals(12_345_678L, parsed.sizeBytes)
        assertEquals(26, parsed.minSdk)
        assertEquals(APK_ASSET_URL, parsed.apkUrl.toString())
    }

    @Test
    fun `rejects unknown schema version`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(schemaVersion = 2)) }
    }

    @Test
    fun `rejects extra fields`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(extra = ",\"redirect\":\"http://evil.test\"")) }
    }

    @Test
    fun `rejects missing fields`() {
        val bytes = """{"schemaVersion":1,"packageName":"$pkg","versionCode":8}""".toByteArray()
        assertThrows(IllegalArgumentException::class.java) { parse(bytes) }
    }

    @Test
    fun `rejects foreign package name`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(packageName = "com.other.app")) }
    }

    @Test
    fun `rejects equal or older version code`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(versionCode = 7)) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(versionCode = 3)) }
    }

    @Test
    fun `rejects implausible size`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(sizeBytes = 0)) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(sizeBytes = MAX_APK + 1)) }
    }

    @Test
    fun `rejects malformed sha256`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(sha256 = "abc")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(sha256 = "z".repeat(64))) }
    }

    @Test
    fun `rejects min sdk above device`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(minSdk = sdk + 1)) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(minSdk = 25)) }
    }

    @Test
    fun `rejects oversized release notes and empty version name`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(releaseNotes = "x".repeat(4097))) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(versionName = "")) }
    }

    @Test
    fun `rejects manifest apk url outside the pinned release asset`() {
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(apkUrl = "https://evil.test/SI-M.apk")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(apkUrl = "http://github.com/$REPO/releases/latest/download/SI-M.apk")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(apkUrl = "https://github.com/$REPO/releases/download/v9/SI-M.apk")) }
        assertThrows(IllegalArgumentException::class.java) { parse(manifest(apkUrl = "https://github.com/$REPO/releases/latest/download/SI-M.apk?x=1")) }
    }

    @Test
    fun `normalises uppercase sha to lowercase`() {
        val parsed = parse(manifest(sha256 = "A".repeat(64)))
        assertEquals("a".repeat(64), parsed.sha256)
    }

    @Test
    fun `safeUrl accepts canonical urls only`() {
        assertEquals(MANIFEST_URL, safeUrl(MANIFEST_URL).toString())
        assertEquals(APK_ASSET_URL, safeUrl(APK_ASSET_URL, apk = true).toString())
        // the manifest URL is not a valid APK asset and vice versa
        assertThrows(IllegalArgumentException::class.java) { safeUrl(MANIFEST_URL, apk = true) }
        assertThrows(IllegalArgumentException::class.java) { safeUrl(APK_ASSET_URL, apk = false) }
    }

    @Test
    fun `validateHop blocks non https, credentials, ports and foreign hosts`() {
        validateHop(URL("https://github.com/x"))
        validateHop(URL("https://objects.githubusercontent.com/x"))
        validateHop(URL("https://github.com:443/x"))

        assertThrows(IllegalArgumentException::class.java) { validateHop(URL("http://github.com/x")) }
        assertThrows(IllegalArgumentException::class.java) { validateHop(URL("https://user:pw@github.com/x")) }
        assertThrows(IllegalArgumentException::class.java) { validateHop(URL("https://github.com:8443/x")) }
        assertThrows(IllegalArgumentException::class.java) { validateHop(URL("https://evil.test/x")) }
        assertThrows(IllegalArgumentException::class.java) { validateHop(URL("https://github.com.evil.test/x")) }
    }
}
