package org.openlist.encrypt.android.config

object EncryptRulePathCodec {
    fun splitAndNormalize(raw: String): List<String> {
        val out = mutableListOf<String>()
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val normalized = normalizeAndValidate(token)
                if (!out.contains(normalized)) out += normalized
            }
        return out
    }

    fun normalizeAndValidate(path: String): String {
        val token = path.trim()
        require(token.isNotEmpty()) { "path cannot be empty" }

        val wildcard = token.endsWith("/*")
        if (token.contains('*') && !wildcard) {
            throw IllegalArgumentException("wildcard only allowed as trailing /*")
        }

        val base = if (wildcard) token.removeSuffix("/*") else token
        val normalizedBase = normalizeBase(base)
        require(normalizedBase.isNotEmpty() && normalizedBase != "/") { "path invalid" }
        return if (wildcard) "$normalizedBase/*" else normalizedBase
    }

    fun basePath(normalizedPath: String): String {
        return if (normalizedPath.endsWith("/*")) normalizedPath.removeSuffix("/*") else normalizedPath
    }

    private fun normalizeBase(path: String): String {
        val s = path.trim()
        if (s.isEmpty()) return ""
        val withLeading = if (s.startsWith('/')) s else "/$s"
        val compact = withLeading.replace(Regex("/+"), "/")
        return if (compact.length > 1) compact.trimEnd('/') else compact
    }
}
