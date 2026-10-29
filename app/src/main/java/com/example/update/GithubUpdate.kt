package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal const val REPO = "jokaje/SI-M"
internal const val MANIFEST_URL = "https://github.com/jokaje/SI-M/releases/latest/download/update.json"
internal const val APK_ASSET_URL = "https://github.com/jokaje/SI-M/releases/latest/download/SI-M.apk"
internal const val API_URL = "https://api.github.com/repos/jokaje/SI-M/releases/latest"
internal const val MAX_JSON = 65536L
internal const val MAX_APK = 200L * 1024 * 1024

/** Hosts a GitHub release download may legitimately hop through. */
internal val ALLOWED_UPDATE_HOSTS = setOf(
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

/** Rejects anything that is not a plain HTTPS URL on an expected GitHub host. */
internal fun validateHop(url: URL) {
    require(url.protocol.equals("https", ignoreCase = true)) { "Unsichere Update-Adresse (kein HTTPS)" }
    require(url.userInfo == null) { "Zugangsdaten in Update-Adresse abgelehnt" }
    require(url.port == -1 || url.port == 443) { "Unerwarteter Port in Update-Adresse" }
    require(url.host?.lowercase() in ALLOWED_UPDATE_HOSTS) { "Fremder Host in Update-Adresse: ${url.host}" }
}

/** Pins the two canonical release assets so a manifest can never point elsewhere. */
internal fun safeUrl(raw: String, apk: Boolean = false): URL {
    val expected = if (apk) APK_ASSET_URL else MANIFEST_URL
    val url = URL(raw)
    validateHop(url)
    require(url.ref == null && url.query == null) { "Parameter in Update-Adresse abgelehnt" }
    require(url.toString() == expected) { "Fremde Release-Adresse" }
    return url
}

private fun connection(url: URL): HttpURLConnection {
    var current = url
    repeat(6) {
        validateHop(current)
        val c = current.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        if (code in 300..399) {
            val location = c.getHeaderField("Location") ?: run { c.disconnect(); error("Umleitung ohne Ziel") }
            c.disconnect()
            current = URL(current, location)
        } else {
            if (code != 200) {
                c.disconnect()
                error("GitHub HTTP $code")
            }
            return c
        }
    }
    error("Zu viele Umleitungen")
}

/**
 * Streams [input] to [output] while hashing, rejecting oversize, truncated and
 * mismatching payloads. Deletes nothing itself: the caller owns the output file.
 */
internal fun copyVerified(
    input: InputStream,
    output: OutputStream,
    expectedSize: Long,
    expectedSha256: String,
    limit: Long = MAX_APK,
    onProgress: (Int) -> Unit = {},
    onChunk: () -> Unit = {},
): Long {
    require(expectedSize in 1..limit) { "Erwartete Größe unplausibel" }
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    val buffer = ByteArray(32768)
    while (true) {
        onChunk()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= expectedSize) { "Download größer als angekündigt" }
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
        onProgress((total * 100 / expectedSize).toInt())
    }
    require(total == expectedSize) { "Download unvollständig ($total von $expectedSize Bytes)" }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    require(actual == expectedSha256.lowercase()) { "APK-Prüfsumme falsch" }
    return total
}

private fun readBounded(url: URL, limit: Long): ByteArray = connection(url).let { c ->
    try {
        require(c.contentLengthLong <= limit) { "Antwort zu groß" }
        c.inputStream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                require(out.size().toLong() + count <= limit) { "Antwort zu groß" }
                out.write(chunk, 0, count)
            }
            out.toByteArray()
        }
    } finally {
        c.disconnect()
    }
}

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: URL,
    val sha256: String,
    val sizeBytes: Long,
    val minSdk: Int,
    val releaseNotes: String,
)

internal val MANIFEST_KEYS = setOf(
    "schemaVersion", "packageName", "versionCode", "versionName",
    "apkUrl", "sha256", "sizeBytes", "minSdk", "releaseNotes",
)

/** Parses and validates the release manifest; anything unexpected is rejected. */
internal fun parseManifest(bytes: ByteArray, installedCode: Long, sdk: Int, packageName: String): UpdateManifest {
    require(bytes.size <= MAX_JSON) { "Manifest zu groß" }
    val obj = JSONObject(bytes.toString(Charsets.UTF_8))
    require(obj.keys().asSequence().toSet() == MANIFEST_KEYS) { "Ungültiges Manifest (Felder)" }
    require(obj.getInt("schemaVersion") == 1) { "Unbekanntes Manifest-Schema" }
    require(obj.getString("packageName") == packageName) { "Manifest für fremdes Paket" }
    val code = obj.getLong("versionCode")
    val size = obj.getLong("sizeBytes")
    val minimum = obj.getInt("minSdk")
    val hash = obj.getString("sha256")
    val name = obj.getString("versionName")
    val notes = obj.getString("releaseNotes")
    require(code > installedCode) { "Kein neueres Update (${code} <= $installedCode)" }
    require(size in 1..MAX_APK) { "Unplausible APK-Größe" }
    require(minimum in 26..sdk) { "Update verlangt neueres Android (minSdk $minimum)" }
    require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Ungültige Prüfsumme im Manifest" }
    require(name.length in 1..80) { "Ungültiger Versionsname" }
    require(notes.length <= 4096) { "Änderungsnotizen zu lang" }
    return UpdateManifest(code, name, safeUrl(obj.getString("apkUrl"), true), hash.lowercase(), size, minimum, notes)
}

/**
 * Signing certificates of a [PackageInfo] as printable strings.
 * API < 28 exposes them through the deprecated `signatures` array,
 * newer platforms through [android.content.pm.SigningInfo].
 */
internal fun signerCertificates(info: PackageInfo): Set<String> =
    if (Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: emptySet()
    } else {
        @Suppress("DEPRECATION")
        info.signatures?.map { it.toCharsString() }?.toSet() ?: emptySet()
    }

class GithubUpdate(private val context: Context) {
    private val pm get() = context.packageManager

    private val installed
        get() = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        }

    private fun version(): Long =
        if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode
        else @Suppress("DEPRECATION") installed.versionCode.toLong()

    private val folder get() = File(context.cacheDir, "updates").apply { mkdirs() }

    fun cleanup() {
        folder.listFiles()?.forEach { it.delete() }
    }

    suspend fun check(): UpdateManifest = withContext(Dispatchers.IO) {
        val release = JSONObject(readBounded(URL(API_URL), MAX_JSON).toString(Charsets.UTF_8))
        val assets = release.getJSONArray("assets")
        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }
        require(names.count { it == "update.json" } == 1) { "Release ohne update.json" }
        require(names.count { it == "SI-M.apk" } == 1) { "Release ohne SI-M.apk" }
        parseManifest(
            readBounded(safeUrl(MANIFEST_URL), MAX_JSON),
            version(),
            Build.VERSION.SDK_INT,
            context.packageName,
        )
    }

    suspend fun download(manifest: UpdateManifest, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            cleanup()
            val destination = File(folder, "SI-M.apk")
            try {
                val coroutine = currentCoroutineContext()
                val c = connection(manifest.apkUrl)
                try {
                    require(c.contentLengthLong <= manifest.sizeBytes) { "APK größer als angekündigt" }
                    c.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            copyVerified(
                                input = input,
                                output = output,
                                expectedSize = manifest.sizeBytes,
                                expectedSha256 = manifest.sha256,
                                limit = MAX_APK,
                                onProgress = { percent -> onProgress(percent) },
                                onChunk = { coroutine.ensureActive() },
                            )
                        }
                    }
                } finally {
                    c.disconnect()
                }
                verifyApk(destination, manifest)
                destination
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
        }

    /** Checks hash, package, version, minSdk and the signing certificate of the *installed* app. */
    fun verifyApk(file: File, manifest: UpdateManifest) {
        require(file.isFile && file.length() == manifest.sizeBytes) { "APK-Größe falsch" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val block = ByteArray(32768)
            while (true) {
                val n = input.read(block)
                if (n < 0) break
                digest.update(block, 0, n)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == manifest.sha256) { "APK-Prüfsumme falsch" }
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val apk = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.path, flags)
        }
        requireNotNull(apk) { "APK nicht lesbar" }
        require(apk.packageName == context.packageName) { "APK für fremdes Paket" }
        val apkVersion = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode
        else @Suppress("DEPRECATION") apk.versionCode.toLong()
        require(apkVersion == manifest.versionCode) { "APK-Version weicht vom Manifest ab" }
        require((apk.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "APK verlangt neueres Android" }
        require(manifest.versionCode > version()) { "Update nicht neuer als installierte App" }
        val current = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, flags)
        }
        val installedSet = signerCertificates(current)
        val freshSet = signerCertificates(apk)
        require(installedSet.isNotEmpty()) { "Installierte Signatur fehlt" }
        require(freshSet.isNotEmpty()) { "APK-Signatur fehlt" }
        require(installedSet == freshSet) { "APK-Signatur stimmt nicht mit der installierten App überein" }
    }

    /** Opens the system installer. Returns false when the user must allow unknown sources first. */
    fun install(file: File, manifest: UpdateManifest): Boolean {
        verifyApk(file, manifest)
        if (Build.VERSION.SDK_INT >= 26 && !pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
