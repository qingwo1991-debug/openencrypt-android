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
            .mapIndexedNotNull { idx, r ->
                val parsed = runCatching { EncryptRulePathCodec.normalizeAndValidate(r.path) }.getOrNull()
                if (parsed == null) {
                    errors += "encrypt_rules[$idx].path invalid"
                    null
                } else {
                    idx to parsed
                }
            }

        normalized.forEach { (idx, p) ->
            if (p.isEmpty() || p == "/" || EncryptRulePathCodec.basePath(p) == "/") {
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
        if (config.openlist.https && config.openlist.h2c) {
            errors += "openlist.https conflicts with openlist.h2c"
        }
        return errors
    }

    private fun isOverlap(a: String, b: String): Boolean {
        val aBase = EncryptRulePathCodec.basePath(a)
        val bBase = EncryptRulePathCodec.basePath(b)
        if (aBase == bBase) return true
        return aBase.startsWith("$bBase/") || bBase.startsWith("$aBase/")
    }
}
