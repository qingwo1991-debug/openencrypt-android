package org.openlist.encrypt.android.update

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class UpdateDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    fun downloadText(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "openencrypt-android")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    fun downloadFile(url: String, versionTag: String, fileName: String): File? {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "openencrypt-android")
            .build()

        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(context.cacheDir, "updates/$versionTag")
        dir.mkdirs()
        val target = File(dir, safeName)

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            target.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        return target
    }
}
