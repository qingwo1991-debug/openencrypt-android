package org.openlist.encrypt.android.update

data class GithubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long
)

data class GithubRelease(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val assets: List<GithubReleaseAsset>
)

data class UpdateDecision(
    val hasUpdate: Boolean,
    val targetTag: String?,
    val reason: String,
    val apkAsset: GithubReleaseAsset? = null,
    val checksumAsset: GithubReleaseAsset? = null
)
