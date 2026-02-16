package org.openlist.encrypt.android.runtime

import android.os.Build
import android.content.Context
import java.io.File

class NativeBinaryInstaller(private val context: Context) {
    private val outDir = File(context.filesDir, "openencrypt/bin")
    private val assetDirCache = mutableMapOf<String, Set<String>>()
    private val reportFile = File(outDir, "install-report.txt")

    data class InstallReport(
        val ok: Boolean,
        val detail: String
    )

    fun installIfPresent(): InstallReport {
        return runCatching {
            outDir.mkdirs()
            val details = mutableListOf<String>()
            var allOk = true
            installBinary("openlist-runtime").also {
                details += "${it.binary}:${it.detail}"
                allOk = allOk && it.ok
            }
            installBinary("openencrypt-gateway").also {
                details += "${it.binary}:${it.detail}"
                allOk = allOk && it.ok
            }
            val report = InstallReport(allOk, details.joinToString("; "))
            writeReport(report)
            report
        }.getOrElse { e ->
            val report = InstallReport(false, "install exception: ${e.message ?: "unknown"}")
            writeReport(report)
            report
        }
    }

    private data class BinaryResult(val binary: String, val ok: Boolean, val detail: String)

    private fun installBinary(binaryName: String): BinaryResult {
        val target = File(outDir, binaryName)
        val paths = buildList {
            supportedAbis().forEach { abi -> add("bin/$abi/$binaryName") }
            add("bin/$binaryName")
        }
        val copiedFrom = paths.firstOrNull { copyAssetIfExists(it, target) }
        if (copiedFrom == null) {
            if (target.exists()) target.delete()
            return BinaryResult(
                binary = binaryName,
                ok = false,
                detail = "asset missing, searched=${paths.joinToString(",")}"
            )
        }
        val execOk = target.exists() && target.canExecute()
        return if (execOk) {
            BinaryResult(binaryName, true, "copied from $copiedFrom")
        } else {
            BinaryResult(binaryName, false, "copied from $copiedFrom but not executable")
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

    fun readLastReport(): String {
        return if (reportFile.exists()) reportFile.readText() else "no install report"
    }

    private fun writeReport(report: InstallReport) {
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(
            "ok=${report.ok}\nabis=${supportedAbis().joinToString(",")}\noutDir=${outDir.absolutePath}\ndetail=${report.detail}\n"
        )
    }
}
