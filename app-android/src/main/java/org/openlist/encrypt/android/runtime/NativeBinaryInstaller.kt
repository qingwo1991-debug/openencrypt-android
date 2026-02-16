package org.openlist.encrypt.android.runtime

import android.os.Build
import android.content.Context
import java.io.File

class NativeBinaryInstaller(private val context: Context) {
    private val outDir = File(context.filesDir, "openencrypt/bin")
    private val assetDirCache = mutableMapOf<String, Set<String>>()

    fun installIfPresent() {
        outDir.mkdirs()
        installBinary("openlist-runtime")
        installBinary("openencrypt-gateway")
    }

    private fun installBinary(binaryName: String) {
        val target = File(outDir, binaryName)
        val paths = buildList {
            supportedAbis().forEach { abi -> add("bin/$abi/$binaryName") }
            add("bin/$binaryName")
        }
        val copied = paths.any { copyAssetIfExists(it, target) }
        if (!copied && target.exists()) {
            target.delete()
        }
    }

    private fun copyAssetIfExists(assetPath: String, target: File): Boolean {
        if (!assetExists(assetPath)) return false

        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        target.setReadable(true, false)
        target.setExecutable(true, true)
        return true
    }

    private fun assetExists(assetPath: String): Boolean {
        val dir = assetPath.substringBeforeLast('/', "")
        val name = assetPath.substringAfterLast('/')
        val files = assetDirCache.getOrPut(dir) {
            runCatching { context.assets.list(dir)?.toSet().orEmpty() }.getOrDefault(emptySet())
        }
        return files.contains(name)
    }

    private fun supportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("arm64-v8a", "armeabi-v7a") }
    }
}
