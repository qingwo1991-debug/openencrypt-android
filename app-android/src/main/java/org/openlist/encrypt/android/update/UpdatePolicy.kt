package org.openlist.encrypt.android.update

object UpdatePolicy {
    private val stableTag = Regex("^v\\d+\\.\\d+\\.\\d+$")

    fun isStableTag(tag: String): Boolean = stableTag.matches(tag)

    fun isNewer(currentTag: String, incomingTag: String): Boolean {
        return SemVer.parse(incomingTag) > SemVer.parse(currentTag)
    }

    fun selectAbiAsset(assets: List<GithubReleaseAsset>, abi: String): GithubReleaseAsset? {
        val suffix = "-$abi.apk"
        return assets.firstOrNull { it.name.endsWith(suffix) }
            ?: assets.firstOrNull { it.name.contains(abi) && it.name.endsWith(".apk") }
    }

    fun selectChecksumAsset(assets: List<GithubReleaseAsset>): GithubReleaseAsset? {
        return assets.firstOrNull { it.name == "checksums.txt" }
    }
}

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        return compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
    }

    companion object {
        fun parse(tag: String): SemVer {
            val clean = tag.removePrefix("v")
            val parts = clean.split('.')
            require(parts.size == 3) { "invalid semver tag: $tag" }
            return SemVer(
                major = parts[0].toIntOrNull() ?: 0,
                minor = parts[1].toIntOrNull() ?: 0,
                patch = parts[2].toIntOrNull() ?: 0
            )
        }
    }
}
