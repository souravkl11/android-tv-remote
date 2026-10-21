package com.ryzen.tvremote.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/souravkl11/android-tv-remote/releases/latest"

    suspend fun checkForUpdate(currentVersionName: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "TVRemote-Android")
            }

            if (connection.responseCode != 200) {
                Log.w(TAG, "GitHub API returned HTTP ${connection.responseCode}")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val htmlUrl = json.optString("html_url", "")
            val body = json.optString("body", "")

            var downloadUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (isNewerVersion(tagName, currentVersionName)) {
                return@withContext AppUpdateInfo(
                    latestVersion = tagName,
                    releaseNotes = body,
                    releaseUrl = htmlUrl,
                    apkDownloadUrl = downloadUrl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        }
        null
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val cleanRemote = remote.trim().removePrefix("v")
        val cleanCurrent = current.trim().removePrefix("v")
        if (cleanRemote == cleanCurrent) return false

        val remoteParts = cleanRemote.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
