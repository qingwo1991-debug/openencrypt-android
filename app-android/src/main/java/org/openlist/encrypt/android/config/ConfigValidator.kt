package org.openlist.encrypt.android.config

object ConfigValidator {
    fun validatePorts(openlistPort: Int, gatewayPort: Int): List<String> {
        val errors = mutableListOf<String>()
        if (openlistPort !in 1..65535) errors += "openlist.port out of range"
        if (gatewayPort !in 1..65535) errors += "gateway.port out of range"
        if (openlistPort == gatewayPort) errors += "openlist.port conflicts with gateway.port"
        return errors
    }

    fun validateEncryptRules(rules: List<EncryptRule>): List<String> {
        val errors = mutableListOf<String>()
        val normalized = rules
            .filter { it.enable }
            .mapIndexed { idx, r -> idx to normalizePath(r.path) }

        normalized.forEach { (idx, p) ->
            if (p.isEmpty() || p == "/") {
                errors += "encrypt_rules[$idx].path invalid"
            }
        }

        for (i in normalized.indices) {
            for (j in i + 1 until normalized.size) {
                val (iIdx, iPath) = normalized[i]
                val (jIdx, jPath) = normalized[j]
                if (isOverlap(iPath, jPath)) {
                    errors += "encrypt_rules[$iIdx].path overlaps encrypt_rules[$jIdx].path"
                }
            }
        }
        return errors
    }

    fun validate(config: AppRuntimeConfig): List<String> {
        val errors = mutableListOf<String>()
        errors += validatePorts(config.openlist.port, config.gateway.port)
        errors += validateEncryptRules(config.encryptRules)
        return errors
    }

    private fun normalizePath(path: String): String {
        val s = path.trim()
        if (s.isEmpty()) return ""
        val withLeading = if (s.startsWith('/')) s else "/$s"
        return withLeading.replace(Regex("/+"), "/").trimEnd('/')
    }

    private fun isOverlap(a: String, b: String): Boolean {
        if (a == b) return true
        return a.startsWith("$b/") || b.startsWith("$a/")
    }
}
