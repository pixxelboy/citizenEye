package com.citizeneye.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val DEFAULT_UPDATE_MANIFEST_URL = "https://pixxelboy.github.io/citizenEye/update.json"
private const val CHECK_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: List<String>,
    val mandatory: Boolean,
    val publishedAt: String,
    val sha256: String?
)

sealed interface UpdateCheckResult {
    data object NoUpdate : UpdateCheckResult
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

sealed interface UpdateInstallResult {
    data object InstallerStarted : UpdateInstallResult
    data object UnknownSourcesPermissionRequired : UpdateInstallResult
    data class Failed(val reason: String) : UpdateInstallResult
}

object UpdateManifestParser {
    fun parse(json: String): UpdateManifest? = runCatching {
        val root = JSONObject(json)
        val versionCode = root.optLong("versionCode", -1L)
        val versionName = root.optString("versionName").trim()
        val apkUrl = root.optString("apkUrl").trim()
        val publishedAt = root.optString("publishedAt").trim()
        if (versionCode <= 0L || versionName.isBlank() || apkUrl.isBlank()) return null
        if (!apkUrl.startsWith("https://")) return null
        UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            releaseNotes = root.optJSONArray("releaseNotes").asStringList(),
            mandatory = root.optBoolean("mandatory", false),
            publishedAt = publishedAt,
            sha256 = root.optString("sha256").trim().takeIf { it.matches(Regex("^[A-Fa-f0-9]{64}$")) }
        )
    }.getOrNull()
}

object UpdatePolicy {
    fun isUpdateAvailable(remoteVersionCode: Long, currentVersionCode: Long): Boolean =
        remoteVersionCode > currentVersionCode
}

class AppUpdateManager(
    private val context: Context,
    private val manifestUrl: String = DEFAULT_UPDATE_MANIFEST_URL,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val fetchText: (String) -> String = ::httpGetText,
    private val downloadBytes: (String, File) -> Unit = ::httpDownloadToFile
) {
    private val prefs = context.getSharedPreferences("citizeneye_updates", Context.MODE_PRIVATE)

    fun currentVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    fun shouldCheck(): Boolean {
        val last = prefs.getLong("last_check_at", 0L)
        return nowMillis() - last >= CHECK_INTERVAL_MILLIS
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!manifestUrl.startsWith("https://")) return@withContext UpdateCheckResult.Failed("Manifest non sécurisé")
        if (!force && !shouldCheck()) return@withContext UpdateCheckResult.NoUpdate
        prefs.edit().putLong("last_check_at", nowMillis()).apply()
        runCatching {
            val manifest = UpdateManifestParser.parse(fetchText(manifestUrl))
                ?: return@withContext UpdateCheckResult.Failed("Manifest invalide")
            if (UpdatePolicy.isUpdateAvailable(manifest.versionCode, currentVersionCode())) {
                UpdateCheckResult.UpdateAvailable(manifest)
            } else {
                UpdateCheckResult.NoUpdate
            }
        }.getOrElse { UpdateCheckResult.Failed(it.message ?: "Recherche de mise à jour impossible") }
    }

    suspend fun downloadAndStartInstall(manifest: UpdateManifest): UpdateInstallResult = withContext(Dispatchers.IO) {
        if (!UpdatePolicy.isUpdateAvailable(manifest.versionCode, currentVersionCode())) return@withContext UpdateInstallResult.Failed("Version déjà installée")
        if (!manifest.apkUrl.startsWith("https://")) return@withContext UpdateInstallResult.Failed("APK non sécurisé")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            openUnknownSourcesSettings()
            return@withContext UpdateInstallResult.UnknownSourcesPermissionRequired
        }
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(dir, "citizeneye-${manifest.versionName}.apk")
            downloadBytes(manifest.apkUrl, apkFile)
            manifest.sha256?.let { expected ->
                val actual = sha256(apkFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    apkFile.delete()
                    return@withContext UpdateInstallResult.Failed("Téléchargement invalide")
                }
            }
            launchInstaller(apkFile)
            UpdateInstallResult.InstallerStarted
        }.getOrElse { UpdateInstallResult.Failed(it.message ?: "Installation impossible") }
    }

    fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw IllegalStateException("Aucun installateur Android disponible", error)
        }
    }
}

private fun JSONArray?.asStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).trim().takeIf { it.isNotBlank() } }
}

private fun httpGetText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        requestMethod = "GET"
        instanceFollowRedirects = true
    }
    return connection.use { conn ->
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

private fun httpDownloadToFile(url: String, destination: File) {
    val tmp = File(destination.parentFile, "${destination.name}.tmp")
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        requestMethod = "GET"
        instanceFollowRedirects = true
    }
    connection.use { conn ->
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
    }
    if (!tmp.renameTo(destination)) {
        tmp.copyTo(destination, overwrite = true)
        tmp.delete()
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private inline fun <T : HttpURLConnection, R> T.use(block: (T) -> R): R = try {
    block(this)
} finally {
    disconnect()
}
