package org.openlist.encrypt.android.config

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigValidatorTest {
    @Test
    fun detectsPortConflict() {
        val errors = ConfigValidator.validatePorts(5344, 5344)
        assertTrue(errors.any { it.contains("conflicts") })
    }

    @Test
    fun detectsEncryptPathOverlap() {
        val rules = listOf(
            EncryptRule(path = "/media", password = "a", enable = true),
            EncryptRule(path = "/media/movies", password = "b", enable = true)
        )

        val errors = ConfigValidator.validateEncryptRules(rules)
        assertTrue(errors.any { it.contains("overlaps") })
    }

    @Test
    fun acceptsTrailingWildcardAndCommaExpansion() {
        val paths = EncryptRulePathCodec.splitAndNormalize("/123/encrypt/*, /abc ,abc")
        assertTrue(paths.contains("/123/encrypt/*"))
        assertTrue(paths.contains("/abc"))
    }

    @Test
    fun rejectsInvalidWildcardPlacement() {
        try {
            EncryptRulePathCodec.normalizeAndValidate("/123/*/x")
            fail("expected invalid wildcard")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
