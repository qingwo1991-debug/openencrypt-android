package org.openlist.encrypt.android.update

import java.io.File
import java.security.MessageDigest

object UpdateVerifier {
    fun parseChecksums(content: String): Map<String, String> {
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val hash = parts[0].lowercase()
                val file = parts[1].removePrefix("*").trim()
                if (hash.length != 64 || file.isEmpty()) return@mapNotNull null
                file to hash
            }
            .toMap()
    }

    fun verifySha256(file: File, expectedHex: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex.equals(expectedHex.lowercase(), ignoreCase = true)
    }
}
