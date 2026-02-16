package org.openlist.encrypt.android.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReleaseFetchResult(
    val release: GithubRelease? = null,
    val error: String? = null
)

class GithubReleaseChecker(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    fun fetchLatestStable(): ReleaseFetchResult {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "openencrypt-android")
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
                    val detail = listOf("http=${resp.code}", msg.take(120).trim()).filter { it.isNotBlank() }
                    return ReleaseFetchResult(error = detail.joinToString(", "))
                }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name")
                if (!UpdatePolicy.isStableTag(tagName)) {
                    return ReleaseFetchResult(error = "latest release is not a stable tag: $tagName")
                }

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

                ReleaseFetchResult(
                    release = GithubRelease(
                        tagName = tagName,
                        name = json.optString("name", tagName),
                        publishedAt = json.optString("published_at"),
                        assets = assets
                    )
                )
            }
        }.getOrElse { e ->
            ReleaseFetchResult(error = e.message ?: "network error")
        }
    }
}
