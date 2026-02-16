package org.openlist.encrypt.android.update

import android.content.Context
import android.os.Build
import org.openlist.encrypt.android.BuildConfig
import org.openlist.encrypt.android.config.DEFAULT_UPDATE_GITHUB_REPO
import org.openlist.encrypt.android.config.ConfigRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UpdateExecutionResult(
    val stage: String,
    val ok: Boolean,
    val detail: String
)

class UpdateCoordinator(
    private val context: Context,
    private val configRepository: ConfigRepository = ConfigRepository(context),
    private val historyStore: UpdateHistoryStore = UpdateHistoryStore(context),
    private val downloader: UpdateDownloader = UpdateDownloader(context),
    private val planner: UpdatePlanner = UpdatePlanner(),
    private val verifier: UpdateArtifactVerifier = UpdateArtifactVerifier(),
    private val installer: ApkInstaller = ApkInstaller(context)
) {
    private var lastFetchError: String? = null

    fun checkLatest(): UpdateExecutionResult {
        val release = fetchRelease()
            ?: return record("check", ok = false, detail = "latest release unavailable${lastFetchError?.let { " ($it)" } ?: ""}")
        val currentTag = currentTag()
        val decision = planner.decide(currentTag, selectAbi(), release)
        return if (decision.hasUpdate) {
            record("check", ok = true, detail = "update available ${release.tagName}")
        } else {
            record("check", ok = true, detail = decision.reason)
        }
    }

    fun checkDownloadAndInstall(): UpdateExecutionResult {
        val release = fetchRelease()
            ?: return record("install", ok = false, detail = "latest release unavailable${lastFetchError?.let { " ($it)" } ?: ""}")

        val decision = planner.decide(currentTag(), selectAbi(), release)
        if (!decision.hasUpdate) {
            return record("install", ok = true, detail = decision.reason)
        }

        val apkAsset = decision.apkAsset
            ?: return record("install", ok = false, detail = "missing apk asset")
        val checksumAsset = decision.checksumAsset
            ?: return record("install", ok = false, detail = "missing checksums.txt")

        val checksumText = downloader.downloadText(checksumAsset.browserDownloadUrl)
            ?: return record("install", ok = false, detail = "failed to download checksums")
        val checksums = UpdateVerifier.parseChecksums(checksumText)
        val expected = checksums[apkAsset.name]
            ?: return record("install", ok = false, detail = "checksum entry missing for ${apkAsset.name}")

        val apkFile = downloader.downloadFile(
            url = apkAsset.browserDownloadUrl,
            versionTag = decision.targetTag ?: "unknown",
            fileName = apkAsset.name
        ) ?: return record("install", ok = false, detail = "failed to download apk")

        val verifyErr = verifier.verify(context, apkFile, expected)
        if (verifyErr != null) {
            return record("install", ok = false, detail = verifyErr)
        }

        val install = installer.install(apkFile)
        return record("install", ok = install.started, detail = install.message)
    }

    private fun fetchRelease(): GithubRelease? {
        val cfg = configRepository.loadOrDefault()
        val repo = normalizeRepo(cfg.update.githubRepo.trim())
        val split = repo.split('/')
        if (split.size != 2 || split.any { it.isBlank() }) {
            lastFetchError = "invalid update repo: $repo"
            return null
        }

        val fetched = GithubReleaseChecker(owner = split[0], repo = split[1]).fetchLatestStable()
        lastFetchError = fetched.error
        return fetched.release
    }

    private fun normalizeRepo(repo: String): String {
        if (repo.isBlank() || repo == "owner/openencrypt-android") {
            return DEFAULT_UPDATE_GITHUB_REPO
        }
        return repo
    }

    private fun currentTag(): String {
        val version = BuildConfig.VERSION_NAME.removePrefix("v").trim()
        return if (Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)) "v$version" else "v0.0.0"
    }

    private fun selectAbi(): String {
        val preferred = listOf("arm64-v8a", "armeabi-v7a")
        val deviceAbis = Build.SUPPORTED_ABIS.toList()
        return preferred.firstOrNull { it in deviceAbis } ?: deviceAbis.firstOrNull() ?: "arm64-v8a"
    }

    private fun record(stage: String, ok: Boolean, detail: String): UpdateExecutionResult {
        historyStore.append(
            UpdateHistoryEntry(
                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .format(Date()),
                stage = stage,
                result = if (ok) "ok" else "error",
                detail = detail
            )
        )
        return UpdateExecutionResult(stage = stage, ok = ok, detail = detail)
    }
}
