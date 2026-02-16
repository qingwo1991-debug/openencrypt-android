package org.openlist.encrypt.android.update

import android.content.Context
import java.io.File

class UpdateArtifactVerifier {
    fun verify(
        context: Context,
        apkFile: File,
        expectedSha256: String
    ): String? {
        if (!UpdateVerifier.verifySha256(apkFile, expectedSha256)) {
            return "checksum mismatch"
        }
        if (!ApkSignatureVerifier.isSameSigner(context, apkFile)) {
            return "signing certificate mismatch"
        }
        return null
    }
}
