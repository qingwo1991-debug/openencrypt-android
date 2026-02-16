package org.openlist.encrypt.android.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkSignatureVerifier {
    fun isSameSigner(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        val pm = context.packageManager
        val current = loadSignerDigests(pm, context.packageName, null)
        val candidate = loadSignerDigests(pm, null, apkFile.absolutePath)
        if (current.isEmpty() || candidate.isEmpty()) return false

        return current == candidate
    }

    private fun loadSignerDigests(
        pm: PackageManager,
        packageName: String?,
        archivePath: String?
    ): Set<String> {
        val info: PackageInfo = when {
            packageName != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                }
            }
            archivePath != null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
                }
            }
            else -> return emptySet()
        } ?: return emptySet()

        val certBytes: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            val sigs = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            sigs.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            (info.signatures ?: emptyArray()).map { it.toByteArray() }
        }

        return certBytes.map { sha256Hex(it) }.toSet()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
