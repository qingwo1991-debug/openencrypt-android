package org.openlist.encrypt.android.ui

import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.view.View
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import org.openlist.encrypt.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var pendingExportContent: String? = null
    private val saveLogLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (content == null || uri == null) return@registerForActivityResult
        val result = writeExport(uri, content)
        val root = view ?: return@registerForActivityResult
        if (result.isSuccess) {
            Snackbar.make(root, getString(R.string.logs_export_success, uri.toString()), Snackbar.LENGTH_LONG).show()
        } else {
            Snackbar.make(
                root,
                getString(R.string.logs_export_failed, result.exceptionOrNull()?.message ?: ""),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? MainUiHost ?: return
        val config = host.currentConfig()

        val configOverview = view.findViewById<MaterialTextView>(R.id.configOverviewSummary)
        val diagnosticsView = view.findViewById<MaterialTextView>(R.id.diagnosticsSummary)
        val updateStatus = view.findViewById<MaterialTextView>(R.id.updateStatus)
        val updateHistory = view.findViewById<MaterialTextView>(R.id.updateHistory)
        val snapshotsView = view.findViewById<MaterialTextView>(R.id.snapshotSummary)
        val logsPreview = view.findViewById<MaterialTextView>(R.id.logsPreview)
        val expertContainer = view.findViewById<View>(R.id.expertContainer)
        val expertSwitch = view.findViewById<SwitchMaterial>(R.id.expertModeSwitch)
        val themeModeGroup = view.findViewById<RadioGroup>(R.id.themeModeGroup)

        val gatewayPortInput = view.findViewById<TextInputEditText>(R.id.gatewayPortInput)
        val parallelInput = view.findViewById<TextInputEditText>(R.id.parallelConcurrencyInput)
        val bufferInput = view.findViewById<TextInputEditText>(R.id.streamBufferInput)
        val upstreamTimeoutInput = view.findViewById<TextInputEditText>(R.id.upstreamTimeoutInput)
        val probeTimeoutInput = view.findViewById<TextInputEditText>(R.id.probeTimeoutInput)
        val probeListBudgetInput = view.findViewById<TextInputEditText>(R.id.probeListBudgetInput)
        val probeStreamBudgetInput = view.findViewById<TextInputEditText>(R.id.probeStreamBudgetInput)
        val backoffInput = view.findViewById<TextInputEditText>(R.id.backoffSecondsInput)
        val fastFailSwitch = view.findViewById<SwitchMaterial>(R.id.fastFailSwitch)
        val updateRepoInput = view.findViewById<TextInputEditText>(R.id.updateRepoInput)
        val updateAutoCheckSwitch = view.findViewById<SwitchMaterial>(R.id.updateAutoCheckSwitch)
        val restoreIndexInput = view.findViewById<TextInputEditText>(R.id.restoreIndexInput)
        val expertJsonInput = view.findViewById<TextInputEditText>(R.id.expertJsonInput)

        gatewayPortInput.setText(config.gateway.port.toString())
        parallelInput.setText(config.gateway.parallelDecryptConcurrency.toString())
        bufferInput.setText(config.gateway.streamBufferKb.toString())
        upstreamTimeoutInput.setText(config.runtime.upstreamTimeoutSeconds.toString())
        probeTimeoutInput.setText(config.runtime.probeTimeoutSeconds.toString())
        probeListBudgetInput.setText(config.runtime.probeBudgetListMs.toString())
        probeStreamBudgetInput.setText(config.runtime.probeBudgetStreamMs.toString())
        backoffInput.setText(config.runtime.upstreamBackoffSeconds.toString())
        fastFailSwitch.isChecked = config.runtime.enableUpstreamFastFail
        updateRepoInput.setText(config.update.githubRepo)
        updateAutoCheckSwitch.isChecked = config.update.autoCheck
        expertJsonInput.setText(host.prettyJson(config))
        updateStatus.text = getString(R.string.update_status_idle)
        logsPreview.movementMethod = ScrollingMovementMethod.getInstance()
        logsPreview.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        fun renderOverview() {
            configOverview.text = getString(
                R.string.page_settings_summary,
                host.schemaFieldCount(),
                host.lastChangedCount()
            )
        }

        fun renderDiagnostics() {
            val baseLines = host.diagnostics().map {
                val status = if (it.ok) "OK" else "FAIL"
                "$status | ${it.key} | ${it.message}"
            }
            diagnosticsView.text = (baseLines + "RUN | runtime.probe | probing...").joinToString("\n")
            host.runRuntimeProbe { probeItems ->
                val probeLines = probeItems.map {
                    val status = if (it.ok) "OK" else "FAIL"
                    "$status | ${it.key} | ${it.message}"
                }
                diagnosticsView.text = (baseLines + probeLines).joinToString("\n")
            }
        }

        fun renderUpdateHistory() {
            val lines = host.latestUpdateHistory(5).map {
                getString(R.string.update_history_line, it.timestamp, it.result, it.detail)
            }
            val body = if (lines.isEmpty()) getString(R.string.update_history_empty) else lines.joinToString("\n")
            updateHistory.text = getString(R.string.update_history_prefix, body)
        }

        fun renderSnapshots() {
            val names = host.snapshotNames(20)
            val rows = names.mapIndexed { idx, name -> "${idx + 1}. $name" }
            snapshotsView.text = if (rows.isEmpty()) {
                getString(R.string.snapshot_empty)
            } else {
                rows.joinToString("\n")
            }
        }

        fun renderLogs() {
            logsPreview.text = getString(R.string.logs_loading)
            host.runLoadLogs { content ->
                logsPreview.text = content
            }
        }

        renderOverview()
        renderDiagnostics()
        renderUpdateHistory()
        renderSnapshots()
        renderLogs()

        val modeViewId = when (host.themeMode()) {
            ThemeModeStore.MODE_LIGHT -> R.id.themeModeLight
            ThemeModeStore.MODE_DARK -> R.id.themeModeDark
            else -> R.id.themeModeSystem
        }
        themeModeGroup.check(modeViewId)
        themeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.themeModeLight -> ThemeModeStore.MODE_LIGHT
                R.id.themeModeDark -> ThemeModeStore.MODE_DARK
                else -> ThemeModeStore.MODE_SYSTEM
            }
            host.setThemeMode(mode)
        }

        expertSwitch.setOnCheckedChangeListener { _, checked ->
            expertContainer.isVisible = checked
        }

        view.findViewById<View>(R.id.expertSaveButton).setOnClickListener {
            val gatewayPort = gatewayPortInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val parallel = parallelInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val buffer = bufferInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val upstreamTimeout = upstreamTimeoutInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val probeTimeout = probeTimeoutInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val probeListBudget = probeListBudgetInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val probeStreamBudget = probeStreamBudgetInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val backoff = backoffInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (listOf(
                    gatewayPort,
                    parallel,
                    buffer,
                    upstreamTimeout,
                    probeTimeout,
                    probeListBudget,
                    probeStreamBudget,
                    backoff
                ).any { it == null }
            ) {
                Snackbar.make(view, getString(R.string.invalid_number), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val current = host.currentConfig()
            val next = current.copy(
                gateway = current.gateway.copy(
                    port = gatewayPort!!,
                    parallelDecryptConcurrency = parallel!!,
                    streamBufferKb = buffer!!
                ),
                runtime = current.runtime.copy(
                    upstreamTimeoutSeconds = upstreamTimeout!!,
                    probeTimeoutSeconds = probeTimeout!!,
                    probeBudgetListMs = probeListBudget!!,
                    probeBudgetStreamMs = probeStreamBudget!!,
                    upstreamBackoffSeconds = backoff!!,
                    enableUpstreamFastFail = fastFailSwitch.isChecked
                ),
                update = current.update.copy(
                    githubRepo = updateRepoInput.text?.toString()?.trim().orEmpty().ifBlank {
                        current.update.githubRepo
                    },
                    autoCheck = updateAutoCheckSwitch.isChecked
                )
            )
            val diff = host.previewChangedKeys(next)
            if (diff.isEmpty()) {
                Snackbar.make(view, getString(R.string.no_changes), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.diff_preview_title)
                .setMessage(diff.take(20).joinToString("\n"))
                .setPositiveButton(R.string.confirm_apply) { _, _ ->
                    val result = host.applyConfig(next)
                    Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                    expertJsonInput.setText(host.prettyJson(host.currentConfig()))
                    renderOverview()
                    renderDiagnostics()
                    renderSnapshots()
                }
                .setNegativeButton(R.string.cancel_apply, null)
                .show()
        }

        view.findViewById<View>(R.id.expertJsonApplyButton).setOnClickListener {
            val raw = expertJsonInput.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                Snackbar.make(view, getString(R.string.expert_json_empty), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val parsed = host.parseJson(raw)
            if (parsed.isFailure) {
                Snackbar.make(
                    view,
                    getString(
                        R.string.save_failed_prefix,
                        parsed.exceptionOrNull()?.message ?: getString(R.string.expert_json_invalid)
                    ),
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val next = parsed.getOrThrow()
            val diff = host.previewChangedKeys(next)
            if (diff.isEmpty()) {
                Snackbar.make(view, getString(R.string.no_changes), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.diff_preview_title)
                .setMessage(diff.take(30).joinToString("\n"))
                .setPositiveButton(R.string.confirm_apply) { _, _ ->
                    val result = host.applyConfig(next)
                    Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                    expertJsonInput.setText(host.prettyJson(host.currentConfig()))
                    renderOverview()
                    renderDiagnostics()
                    renderSnapshots()
                }
                .setNegativeButton(R.string.cancel_apply, null)
                .show()
        }

        view.findViewById<View>(R.id.refreshDiagnosticsButton).setOnClickListener {
            renderDiagnostics()
            Snackbar.make(view, R.string.diagnostics_refreshed, Snackbar.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.checkUpdate).setOnClickListener {
            updateStatus.text = getString(R.string.update_status_running, getString(R.string.update_stage_check))
            host.runUpdateCheck { result ->
                updateStatus.text = result.message
                renderUpdateHistory()
            }
        }

        view.findViewById<View>(R.id.installUpdate).setOnClickListener {
            updateStatus.text = getString(R.string.update_status_running, getString(R.string.update_stage_install))
            host.runUpdateInstall { result ->
                updateStatus.text = result.message
                renderUpdateHistory()
            }
        }

        view.findViewById<View>(R.id.refreshSnapshotsButton).setOnClickListener {
            renderSnapshots()
        }

        view.findViewById<View>(R.id.restoreSnapshotButton).setOnClickListener {
            val oneBased = restoreIndexInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val index = (oneBased ?: 0) - 1
            val snapshots = host.snapshotNames(20)
            if (index !in snapshots.indices) {
                Snackbar.make(view, getString(R.string.snapshot_index_error), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val snapshot = snapshots[index]
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_confirm_title)
                .setMessage(getString(R.string.restore_confirm_body, snapshot))
                .setPositiveButton(R.string.confirm_apply) { _, _ ->
                    val result = host.restoreSnapshot(snapshot)
                    Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                    expertJsonInput.setText(host.prettyJson(host.currentConfig()))
                    renderOverview()
                    renderDiagnostics()
                    renderSnapshots()
                }
                .setNegativeButton(R.string.cancel_apply, null)
                .show()
        }

        view.findViewById<View>(R.id.refreshLogsButton).setOnClickListener {
            renderLogs()
            Snackbar.make(view, R.string.logs_refreshed, Snackbar.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.exportLogsButton).setOnClickListener {
            host.runLoadLogsForExport { content ->
                pendingExportContent = content
                val fileName = "openencrypt-log-export-${stamp()}.txt"
                saveLogLauncher.launch(fileName)
            }
        }
    }

    private fun writeExport(uri: Uri, content: String): Result<Unit> = runCatching {
        requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
            requireNotNull(writer) { "output stream unavailable" }
            writer.write(content)
            writer.flush()
        }
    }

    private fun stamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
}
