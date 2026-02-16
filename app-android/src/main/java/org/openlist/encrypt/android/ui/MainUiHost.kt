package org.openlist.encrypt.android.ui

import org.openlist.encrypt.android.config.AppRuntimeConfig
import org.openlist.encrypt.android.diagnostics.DiagnosticItem
import org.openlist.encrypt.android.update.UpdateHistoryEntry

data class UiActionResult(
    val ok: Boolean,
    val message: String,
    val changedKeys: List<String> = emptyList()
)

interface MainUiHost {
    fun currentConfig(): AppRuntimeConfig
    fun schemaFieldCount(): Int
    fun lastChangedCount(): Int
    fun previewChangedKeys(next: AppRuntimeConfig): List<String>
    fun applyConfig(next: AppRuntimeConfig): UiActionResult
    fun latestUpdateHistory(limit: Int = 5): List<UpdateHistoryEntry>
    fun diagnostics(): List<DiagnosticItem>
    fun snapshotNames(limit: Int = 20): List<String>
    fun restoreSnapshot(snapshotName: String): UiActionResult
    fun requestRuntimeStart(): UiActionResult
    fun requestRuntimeStop(): UiActionResult
    fun runUpdateCheck(onDone: (UiActionResult) -> Unit)
    fun runUpdateInstall(onDone: (UiActionResult) -> Unit)
    fun prettyJson(config: AppRuntimeConfig): String
    fun parseJson(raw: String): Result<AppRuntimeConfig>
}
