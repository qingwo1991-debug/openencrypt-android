package org.openlist.encrypt.android.runtime

import android.content.Context
import java.io.File

class NativeBinaryInstaller(private val context: Context) {
    private val outDir = File(context.filesDir, "openencrypt/bin")

    fun installIfPresent() {
        outDir.mkdirs()
        copyAssetIfExists("bin/openlist-runtime", File(outDir, "openlist-runtime"))
        copyAssetIfExists("bin/openencrypt-gateway", File(outDir, "openencrypt-gateway"))
    }

    private fun copyAssetIfExists(assetPath: String, target: File) {
        val names = runCatching { context.assets.list("bin")?.toSet().orEmpty() }.getOrDefault(emptySet())
        val fileName = assetPath.substringAfterLast('/')
        if (!names.contains(fileName)) return

        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        target.setReadable(true, false)
        target.setExecutable(true, true)
    }
}
