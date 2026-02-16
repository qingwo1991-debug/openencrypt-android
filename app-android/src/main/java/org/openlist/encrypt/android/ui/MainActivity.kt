package org.openlist.encrypt.android.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.openlist.encrypt.android.R
import org.openlist.encrypt.android.config.AppRuntimeConfig
import org.openlist.encrypt.android.config.ConfigRepository
import org.openlist.encrypt.android.config.ConfigValidator
import org.openlist.encrypt.android.config.SchemaFieldRegistry
import org.openlist.encrypt.android.diagnostics.DiagnosticItem
import org.openlist.encrypt.android.service.RuntimeService
import org.openlist.encrypt.android.update.UpdateCoordinator
import org.openlist.encrypt.android.update.UpdateHistoryStore
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), MainUiHost {
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var configRepo: ConfigRepository
    private lateinit var updateCoordinator: UpdateCoordinator
    private lateinit var updateHistoryStore: UpdateHistoryStore

    private var currentConfig: AppRuntimeConfig = AppRuntimeConfig()
    private var schemaFields = 0
    private var lastChanged = 0
    private var currentMenuId = R.id.nav_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.app_name)

        configRepo = ConfigRepository(this)
        updateCoordinator = UpdateCoordinator(this)
        updateHistoryStore = UpdateHistoryStore(this)
        currentConfig = configRepo.loadOrDefault()

        schemaFields = runCatching {
            val registry = SchemaFieldRegistry(this)
            val fields = registry.loadFields()
            val errors = registry.validatePrimaryEditPages(fields)
            require(errors.isEmpty()) { errors.joinToString() }
            fields.size
        }.getOrDefault(0)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener {
            currentMenuId = it.itemId
            switchFragment(it.itemId)
            true
        }
        if (savedInstanceState == null) {
            switchFragment(currentMenuId)
        }
    }

    private fun switchFragment(menuId: Int) {
        val fragment = when (menuId) {
            R.id.nav_dashboard -> DashboardFragment()
            R.id.nav_cloud -> CloudFragment()
            R.id.nav_encrypt -> EncryptFragment()
            R.id.nav_tasks -> TasksFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun currentConfig(): AppRuntimeConfig = currentConfig

    override fun schemaFieldCount(): Int = schemaFields

    override fun lastChangedCount(): Int = lastChanged

    override fun previewChangedKeys(next: AppRuntimeConfig): List<String> {
        return configRepo.diffKeys(currentConfig, next)
    }

    override fun applyConfig(next: AppRuntimeConfig): UiActionResult {
        return runCatching {
            val result = configRepo.saveAtomically(next)
            currentConfig = next
            lastChanged = result.changedKeys.size
            UiActionResult(
                ok = true,
                message = getString(R.string.save_success),
                changedKeys = result.changedKeys
            )
        }.getOrElse { e ->
            UiActionResult(
                ok = false,
                message = getString(R.string.save_failed_prefix, e.message ?: "")
            )
        }
    }

    override fun latestUpdateHistory(limit: Int): List<org.openlist.encrypt.android.update.UpdateHistoryEntry> {
        return updateHistoryStore.latest(limit)
    }

    override fun diagnostics(): List<DiagnosticItem> {
        val validatorErrors = ConfigValidator.validate(currentConfig)
        val historyCount = updateHistoryStore.latest(10).size
        val snapshots = configRepo.listSnapshotNames(100).size
        return listOf(
            DiagnosticItem(
                key = "config.validate",
                ok = validatorErrors.isEmpty(),
                message = if (validatorErrors.isEmpty()) "ok" else validatorErrors.joinToString("; ")
            ),
            DiagnosticItem(
                key = "schema.field_count",
                ok = schemaFields > 0,
                message = schemaFields.toString()
            ),
            DiagnosticItem(
                key = "update.history_entries",
                ok = true,
                message = historyCount.toString()
            ),
            DiagnosticItem(
                key = "backup.snapshot_count",
                ok = true,
                message = snapshots.toString()
            )
        )
    }

    override fun snapshotNames(limit: Int): List<String> = configRepo.listSnapshotNames(limit)

    override fun restoreSnapshot(snapshotName: String): UiActionResult {
        return runCatching {
            val saveResult = configRepo.restoreFromSnapshot(snapshotName)
            currentConfig = configRepo.loadOrDefault()
            lastChanged = saveResult.changedKeys.size
            UiActionResult(
                ok = true,
                message = getString(R.string.restore_success, snapshotName),
                changedKeys = saveResult.changedKeys
            )
        }.getOrElse { e ->
            UiActionResult(
                ok = false,
                message = getString(R.string.restore_failed, e.message ?: "")
            )
        }
    }

    override fun requestRuntimeStart(): UiActionResult {
        val intent = Intent(this, RuntimeService::class.java).apply {
            action = RuntimeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        return UiActionResult(ok = true, message = getString(R.string.runtime_start_requested))
    }

    override fun requestRuntimeStop(): UiActionResult {
        val intent = Intent(this, RuntimeService::class.java).apply {
            action = RuntimeService.ACTION_STOP
        }
        startService(intent)
        return UiActionResult(ok = true, message = getString(R.string.runtime_stop_requested))
    }

    override fun runUpdateCheck(onDone: (UiActionResult) -> Unit) {
        ioExecutor.execute {
            val result = updateCoordinator.checkLatest()
            runOnUiThread {
                onDone(
                    UiActionResult(
                        ok = result.ok,
                        message = getString(R.string.update_status_result, result.stage, result.detail)
                    )
                )
            }
        }
    }

    override fun runUpdateInstall(onDone: (UiActionResult) -> Unit) {
        ioExecutor.execute {
            val result = updateCoordinator.checkDownloadAndInstall()
            runOnUiThread {
                onDone(
                    UiActionResult(
                        ok = result.ok,
                        message = getString(R.string.update_status_result, result.stage, result.detail)
                    )
                )
            }
        }
    }

    override fun prettyJson(config: AppRuntimeConfig): String {
        return configRepo.toPrettyJson(config)
    }

    override fun parseJson(raw: String): Result<AppRuntimeConfig> {
        return runCatching { configRepo.parseJson(raw) }
    }

    override fun themeMode(): Int = ThemeModeStore.read(this)

    override fun setThemeMode(mode: Int) {
        val changed = ThemeModeStore.write(this, mode)
        ThemeModeStore.apply(this)
        if (changed) recreate()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
