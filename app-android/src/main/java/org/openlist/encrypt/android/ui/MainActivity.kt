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
import com.google.android.material.textview.MaterialTextView
import org.openlist.encrypt.android.R
import org.openlist.encrypt.android.config.ConfigRepository
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
        val pageCloud = findViewById<MaterialTextView>(R.id.pageCloud)
        val pageEncrypt = findViewById<MaterialTextView>(R.id.pageEncrypt)
        val pageTasks = findViewById<MaterialTextView>(R.id.pageTasks)
        val pageSettings = findViewById<View>(R.id.pageSettings)
        val dashboardSummary = findViewById<MaterialTextView>(R.id.dashboardSummary)
        val settingsSummary = findViewById<MaterialTextView>(R.id.settingsSummary)
        val updateStatus = findViewById<MaterialTextView>(R.id.updateStatus)
        val updateHistory = findViewById<MaterialTextView>(R.id.updateHistory)
        val checkUpdate = findViewById<MaterialButton>(R.id.checkUpdate)
        val installUpdate = findViewById<MaterialButton>(R.id.installUpdate)

        toolbar.title = getString(R.string.app_name)

        val configRepo = ConfigRepository(this)
        val loaded = configRepo.loadOrDefault()
        val saveResult = runCatching { configRepo.saveAtomically(loaded) }.getOrNull()

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

        dashboardSummary.text = getString(R.string.page_dashboard_summary)
        pageCloud.text = buildString {
            append(getString(R.string.page_cloud_title))
            append("\n\n")
            append(getString(R.string.page_cloud_summary, loaded.openlist.host, loaded.openlist.port))
        }
        pageEncrypt.text = buildString {
            append(getString(R.string.page_encrypt_title))
            append("\n\n")
            append(getString(R.string.page_encrypt_summary, loaded.encryptRules.size, loaded.gateway.port))
        }
        pageTasks.text = buildString {
            append(getString(R.string.page_tasks_title))
            append("\n\n")
            append(
                getString(
                    R.string.page_tasks_summary,
                    loaded.webdav.enable.toString(),
                    loaded.runtime.probeBudgetListMs
                )
            )
        }
        settingsSummary.text = buildString {
            append(getString(R.string.page_settings_title))
            append("\n\n")
            append(
                getString(
                    R.string.page_settings_summary,
                    schemaFields.size,
                    saveResult?.changedKeys?.size ?: 0
                )
            )
        }

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
