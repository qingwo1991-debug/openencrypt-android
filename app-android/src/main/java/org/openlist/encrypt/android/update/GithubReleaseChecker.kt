package org.openlist.encrypt.android.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GithubReleaseChecker(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    fun fetchLatestStable(): GithubRelease? {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "openencrypt-android")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val tagName = json.optString("tag_name")
            if (!UpdatePolicy.isStableTag(tagName)) return null

            val assetsJson = json.optJSONArray("assets") ?: JSONArray()
            val assets = mutableListOf<GithubReleaseAsset>()
            for (i in 0 until assetsJson.length()) {
                val a = assetsJson.getJSONObject(i)
                assets += GithubReleaseAsset(
                    name = a.optString("name"),
                    browserDownloadUrl = a.optString("browser_download_url"),
                    size = a.optLong("size")
                )
            }

            return GithubRelease(
                tagName = tagName,
                name = json.optString("name", tagName),
                publishedAt = json.optString("published_at"),
                assets = assets
            )
        }
    }
}
