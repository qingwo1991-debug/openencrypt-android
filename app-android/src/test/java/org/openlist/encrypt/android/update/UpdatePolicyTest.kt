package org.openlist.encrypt.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun stableTagOnly() {
        assertTrue(UpdatePolicy.isStableTag("v1.2.3"))
        assertFalse(UpdatePolicy.isStableTag("v1.2.3-rc1"))
    }

    @Test
    fun semanticVersionCompare() {
        assertTrue(UpdatePolicy.isNewer("v1.2.3", "v1.3.0"))
        assertFalse(UpdatePolicy.isNewer("v1.2.3", "v1.2.3"))
    }

    @Test
    fun parsesChecksums() {
        val checksums = UpdateVerifier.parseChecksums(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  app-arm64.apk\n"
        )
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            checksums["app-arm64.apk"]
        )
    }
}
