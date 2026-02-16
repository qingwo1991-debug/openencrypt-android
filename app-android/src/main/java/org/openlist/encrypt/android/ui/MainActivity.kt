package org.openlist.encrypt.android.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import org.openlist.encrypt.android.R
import org.openlist.encrypt.android.config.AppRuntimeConfig
import org.openlist.encrypt.android.config.ConfigRepository
import org.openlist.encrypt.android.config.EncryptRule
import org.openlist.encrypt.android.config.SchemaFieldRegistry
import org.openlist.encrypt.android.service.RuntimeService
import org.openlist.encrypt.android.update.UpdateCoordinator
import org.openlist.encrypt.android.update.UpdateHistoryStore
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val pageDashboard = findViewById<View>(R.id.pageDashboard)
        val pageCloud = findViewById<View>(R.id.pageCloud)
        val pageEncrypt = findViewById<View>(R.id.pageEncrypt)
        val pageTasks = findViewById<View>(R.id.pageTasks)
        val pageSettings = findViewById<View>(R.id.pageSettings)
        val dashboardSummary = findViewById<MaterialTextView>(R.id.dashboardSummary)
        val settingsSummary = findViewById<MaterialTextView>(R.id.settingsSummary)
        val updateStatus = findViewById<MaterialTextView>(R.id.updateStatus)
        val updateHistory = findViewById<MaterialTextView>(R.id.updateHistory)
        val checkUpdate = findViewById<MaterialButton>(R.id.checkUpdate)
        val installUpdate = findViewById<MaterialButton>(R.id.installUpdate)

        val cloudHostInput = findViewById<TextInputEditText>(R.id.cloudHostInput)
        val cloudPortInput = findViewById<TextInputEditText>(R.id.cloudPortInput)
        val cloudHttpsSwitch = findViewById<SwitchMaterial>(R.id.cloudHttpsSwitch)
        val cloudSaveButton = findViewById<MaterialButton>(R.id.cloudSaveButton)

        val encryptPathInput = findViewById<TextInputEditText>(R.id.encryptPathInput)
        val encryptPasswordInput = findViewById<TextInputEditText>(R.id.encryptPasswordInput)
        val encryptTypeInput = findViewById<TextInputEditText>(R.id.encryptTypeInput)
        val encryptNameSwitch = findViewById<SwitchMaterial>(R.id.encryptNameSwitch)
        val encryptEnableSwitch = findViewById<SwitchMaterial>(R.id.encryptEnableSwitch)
        val encryptAddRuleButton = findViewById<MaterialButton>(R.id.encryptAddRuleButton)
        val encryptClearRulesButton = findViewById<MaterialButton>(R.id.encryptClearRulesButton)
        val encryptRulesPreview = findViewById<MaterialTextView>(R.id.encryptRulesPreview)

        val tasksWebdavSwitch = findViewById<SwitchMaterial>(R.id.tasksWebdavSwitch)
        val tasksHeaderTimeoutInput = findViewById<TextInputEditText>(R.id.tasksHeaderTimeoutInput)
        val tasksReadIdleInput = findViewById<TextInputEditText>(R.id.tasksReadIdleInput)
        val tasksProbeBudgetInput = findViewById<TextInputEditText>(R.id.tasksProbeBudgetInput)
        val tasksSaveButton = findViewById<MaterialButton>(R.id.tasksSaveButton)

        toolbar.title = getString(R.string.app_name)

        val configRepo = ConfigRepository(this)
        var currentConfig = configRepo.loadOrDefault()
        var lastChangedCount = 0

        val schemaFields = runCatching {
            val registry = SchemaFieldRegistry(this)
            val fields = registry.loadFields()
            val dupErrors = registry.validatePrimaryEditPages(fields)
            if (dupErrors.isNotEmpty()) {
                throw IllegalStateException(dupErrors.joinToString())
            }
            fields
        }.getOrDefault(emptyList())

        val updateCoordinator = UpdateCoordinator(this)
        val updateHistoryStore = UpdateHistoryStore(this)

        fun renderSettingsSummary() {
            settingsSummary.text = getString(
                R.string.page_settings_summary,
                schemaFields.size,
                lastChangedCount
            )
        }

        fun renderUpdateHistory() {
            val lines = updateHistoryStore.latest(5).map {
                getString(R.string.update_history_line, it.timestamp, it.result, it.detail)
            }
            val content = if (lines.isEmpty()) {
                getString(R.string.update_history_empty)
            } else {
                lines.joinToString(separator = "\n")
            }
            updateHistory.text = getString(R.string.update_history_prefix, content)
        }

        fun renderRulesPreview(rules: List<EncryptRule>) {
            val lines = rules.mapIndexed { idx, rule ->
                getString(
                    R.string.encrypt_rule_line,
                    idx + 1,
                    rule.path,
                    rule.encType,
                    rule.encName.toString(),
                    rule.enable.toString()
                )
            }
            val content = if (lines.isEmpty()) {
                getString(R.string.encrypt_rules_empty)
            } else {
                lines.joinToString(separator = "\n")
            }
            encryptRulesPreview.text = getString(R.string.encrypt_rules_preview, content)
        }

        fun fillForms(config: AppRuntimeConfig) {
            cloudHostInput.setText(config.openlist.host)
            cloudPortInput.setText(config.openlist.port.toString())
            cloudHttpsSwitch.isChecked = config.openlist.https

            tasksWebdavSwitch.isChecked = config.webdav.enable
            tasksHeaderTimeoutInput.setText(config.webdav.headerTimeoutMs.toString())
            tasksReadIdleInput.setText(config.webdav.readIdleTimeoutMs.toString())
            tasksProbeBudgetInput.setText(config.runtime.probeBudgetListMs.toString())

            encryptTypeInput.setText("aes-ctr")
            encryptNameSwitch.isChecked = false
            encryptEnableSwitch.isChecked = true
            dashboardSummary.text = getString(R.string.page_dashboard_summary)
            renderRulesPreview(config.encryptRules)
            renderSettingsSummary()
        }

        fun saveConfig(next: AppRuntimeConfig, anchor: View) {
            runCatching {
                val result = configRepo.saveAtomically(next)
                currentConfig = next
                lastChangedCount = result.changedKeys.size
                fillForms(currentConfig)
                Snackbar.make(anchor, getString(R.string.save_success), Snackbar.LENGTH_SHORT).show()
            }.onFailure { e ->
                Snackbar.make(
                    anchor,
                    getString(R.string.save_failed_prefix, e.message ?: ""),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        fun parsePositiveInt(input: TextInputEditText): Int? {
            val raw = input.text?.toString()?.trim().orEmpty()
            return raw.toIntOrNull()
        }

        fillForms(currentConfig)

        fun render(menuId: Int) {
            pageDashboard.visibility = if (menuId == R.id.nav_dashboard) View.VISIBLE else View.GONE
            pageCloud.visibility = if (menuId == R.id.nav_cloud) View.VISIBLE else View.GONE
            pageEncrypt.visibility = if (menuId == R.id.nav_encrypt) View.VISIBLE else View.GONE
            pageTasks.visibility = if (menuId == R.id.nav_tasks) View.VISIBLE else View.GONE
            pageSettings.visibility = if (menuId == R.id.nav_settings) View.VISIBLE else View.GONE
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener {
            render(it.itemId)
            true
        }
        bottomNav.setOnItemReselectedListener {
            if (it.itemId == R.id.nav_dashboard) {
                Snackbar.make(
                    bottomNav,
                    getString(R.string.already_on_dashboard),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        render(R.id.nav_dashboard)

        findViewById<MaterialButton>(R.id.startRuntime).setOnClickListener { v ->
            val intent = Intent(this, RuntimeService::class.java).apply {
                action = RuntimeService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            Snackbar.make(v, getString(R.string.runtime_start_requested), Snackbar.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.stopRuntime).setOnClickListener { v ->
            val intent = Intent(this, RuntimeService::class.java).apply {
                action = RuntimeService.ACTION_STOP
            }
            startService(intent)
            Snackbar.make(v, getString(R.string.runtime_stop_requested), Snackbar.LENGTH_SHORT).show()
        }

        cloudSaveButton.setOnClickListener { v ->
            val host = cloudHostInput.text?.toString()?.trim().orEmpty().ifBlank { "127.0.0.1" }
            val port = parsePositiveInt(cloudPortInput)
            if (port == null) {
                Snackbar.make(v, getString(R.string.invalid_number), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val next = currentConfig.copy(
                openlist = currentConfig.openlist.copy(
                    host = host,
                    port = port,
                    https = cloudHttpsSwitch.isChecked
                )
            )
            saveConfig(next, v)
        }

        encryptAddRuleButton.setOnClickListener { v ->
            val path = encryptPathInput.text?.toString()?.trim().orEmpty()
            val password = encryptPasswordInput.text?.toString()?.trim().orEmpty()
            val encType = encryptTypeInput.text?.toString()?.trim().orEmpty().ifBlank { "aes-ctr" }
            if (path.isBlank() || password.isBlank()) {
                Snackbar.make(
                    v,
                    getString(R.string.save_failed_prefix, getString(R.string.encrypt_required_error)),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (encType != "aes-ctr" && encType != "rc4md5") {
                Snackbar.make(
                    v,
                    getString(R.string.save_failed_prefix, getString(R.string.encrypt_type_error)),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val newRule = EncryptRule(
                path = path,
                password = password,
                encType = encType,
                encName = encryptNameSwitch.isChecked,
                enable = encryptEnableSwitch.isChecked
            )
            val next = currentConfig.copy(
                encryptRules = currentConfig.encryptRules + newRule
            )
            saveConfig(next, v)
        }

        encryptClearRulesButton.setOnClickListener { v ->
            val next = currentConfig.copy(encryptRules = emptyList())
            saveConfig(next, v)
        }

        tasksSaveButton.setOnClickListener { v ->
            val headerTimeout = parsePositiveInt(tasksHeaderTimeoutInput)
            val readIdleTimeout = parsePositiveInt(tasksReadIdleInput)
            val probeBudget = parsePositiveInt(tasksProbeBudgetInput)
            if (headerTimeout == null || readIdleTimeout == null || probeBudget == null) {
                Snackbar.make(v, getString(R.string.invalid_number), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val next = currentConfig.copy(
                webdav = currentConfig.webdav.copy(
                    enable = tasksWebdavSwitch.isChecked,
                    headerTimeoutMs = headerTimeout,
                    readIdleTimeoutMs = readIdleTimeout
                ),
                runtime = currentConfig.runtime.copy(
                    probeBudgetListMs = probeBudget
                )
            )
            saveConfig(next, v)
        }

        fun setUpdateButtonsEnabled(enabled: Boolean) {
            checkUpdate.isEnabled = enabled
            installUpdate.isEnabled = enabled
        }

        fun runUpdateJob(tag: String, block: () -> Unit) {
            updateStatus.text = getString(R.string.update_status_running, tag)
            setUpdateButtonsEnabled(false)
            ioExecutor.execute {
                block()
                runOnUiThread {
                    setUpdateButtonsEnabled(true)
                    renderUpdateHistory()
                }
            }
        }

        checkUpdate.setOnClickListener {
            runUpdateJob(getString(R.string.update_stage_check)) {
                val result = updateCoordinator.checkLatest()
                runOnUiThread {
                    updateStatus.text = getString(
                        R.string.update_status_result,
                        result.stage,
                        result.detail
                    )
                }
            }
        }

        installUpdate.setOnClickListener {
            runUpdateJob(getString(R.string.update_stage_install)) {
                val result = updateCoordinator.checkDownloadAndInstall()
                runOnUiThread {
                    updateStatus.text = getString(
                        R.string.update_status_result,
                        result.stage,
                        result.detail
                    )
                }
            }
        }

        renderUpdateHistory()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
