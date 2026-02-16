package org.openlist.encrypt.android.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val pageTitle = findViewById<MaterialTextView>(R.id.pageTitle)
        val pageSummary = findViewById<MaterialTextView>(R.id.pageSummary)

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

        val pages = mapOf(
            R.id.nav_dashboard to (
                "Dashboard" to
                    "Runtime orchestration and diagnostics entry point."
                ),
            R.id.nav_cloud to (
                "Cloud" to
                    "OpenList endpoint ${loaded.openlist.host}:${loaded.openlist.port}."
                ),
            R.id.nav_encrypt to (
                "Encrypt" to
                    "Rules: ${loaded.encryptRules.size}, gateway port ${loaded.gateway.port}."
                ),
            R.id.nav_tasks to (
                "Tasks" to
                    "WebDAV enabled: ${loaded.webdav.enable}, list budget ${loaded.runtime.probeBudgetListMs}ms."
                ),
            R.id.nav_settings to (
                "Settings" to
                    "Schema fields ${schemaFields.size}, changed keys ${saveResult?.changedKeys?.size ?: 0}."
                )
        )

        fun render(menuId: Int) {
            val entry = pages.getValue(menuId)
            pageTitle.text = entry.first
            pageSummary.text = entry.second
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener {
            render(it.itemId)
            true
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
            Snackbar.make(v, "Runtime start requested", Snackbar.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.stopRuntime).setOnClickListener { v ->
            val intent = Intent(this, RuntimeService::class.java).apply {
                action = RuntimeService.ACTION_STOP
            }
            startService(intent)
            Snackbar.make(v, "Runtime stop requested", Snackbar.LENGTH_SHORT).show()
        }
    }
}
