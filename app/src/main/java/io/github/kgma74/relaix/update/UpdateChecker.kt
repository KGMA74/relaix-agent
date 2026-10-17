package io.github.kgma74.relaix.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A version of the app newer than the one running, and where to get it. */
data class Update(val version: String, val downloadUrl: String)

/**
 * Asks GitHub for the app's latest release. Releases are published from the
 * relaix-agent repository (`make release`), each carrying the signed APKs;
 * downloading one opens the browser, and Android installs it over this one —
 * same signing key, so the enrollment and the PIN vault are kept.
 */
object UpdateChecker {
    const val RELEASES = "https://github.com/KGMA74/relaix-agent/releases/latest"
    private const val LATEST = "https://api.github.com/repos/KGMA74/relaix-agent/releases/latest"

    /** The running version, from the package. */
    fun current(context: Context): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName.orEmpty()
    }.getOrDefault("")

    /** A newer release, or null: up to date, or GitHub could not be reached. */
    suspend fun check(context: Context): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return@runCatching null
                val release = JSONObject(conn.inputStream.bufferedReader().readText())
                val version = release.optString("tag_name").removePrefix("v")
                if (!isNewer(version, current(context))) return@runCatching null
                val assets = release.optJSONArray("assets")
                var url = RELEASES
                for (i in 0 until (assets?.length() ?: 0)) {
                    val asset = assets!!.getJSONObject(i)
                    if (asset.optString("name") == "relaix.apk") url = asset.optString("browser_download_url", RELEASES)
                }
                Update(version, url)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Compares dotted versions number by number: 1.10.0 is newer than 1.9.2. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        if (candidate.isBlank()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
