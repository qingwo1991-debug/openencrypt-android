package org.openlist.encrypt.android.update

class UpdatePlanner {
    fun decide(
        currentTag: String,
        abi: String,
        release: GithubRelease?
    ): UpdateDecision {
        if (release == null) {
            return UpdateDecision(hasUpdate = false, targetTag = null, reason = "latest release unavailable")
        }
        if (!UpdatePolicy.isStableTag(release.tagName)) {
            return UpdateDecision(hasUpdate = false, targetTag = null, reason = "release is not stable")
        }
        if (!UpdatePolicy.isNewer(currentTag, release.tagName)) {
            return UpdateDecision(hasUpdate = false, targetTag = release.tagName, reason = "already up to date")
        }

        val apk = UpdatePolicy.selectAbiAsset(release.assets, abi)
            ?: return UpdateDecision(
                hasUpdate = false,
                targetTag = release.tagName,
                reason = "no ABI-matching asset"
            )

        val checksums = UpdatePolicy.selectChecksumAsset(release.assets)
            ?: return UpdateDecision(
                hasUpdate = false,
                targetTag = release.tagName,
                reason = "missing checksums.txt"
            )

        return UpdateDecision(
            hasUpdate = true,
            targetTag = release.tagName,
            reason = "stable update available",
            apkAsset = apk,
            checksumAsset = checksums
        )
    }
}
