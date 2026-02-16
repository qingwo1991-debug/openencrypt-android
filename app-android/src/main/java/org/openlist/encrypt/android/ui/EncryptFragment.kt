package org.openlist.encrypt.android.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import org.openlist.encrypt.android.R
import org.openlist.encrypt.android.config.EncryptRule

class EncryptFragment : Fragment(R.layout.fragment_encrypt) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? MainUiHost ?: return

        val encryptPathInput = view.findViewById<TextInputEditText>(R.id.encryptPathInput)
        val encryptPasswordInput = view.findViewById<TextInputEditText>(R.id.encryptPasswordInput)
        val encryptTypeInput = view.findViewById<TextInputEditText>(R.id.encryptTypeInput)
        val encryptNameSwitch = view.findViewById<SwitchMaterial>(R.id.encryptNameSwitch)
        val encryptEnableSwitch = view.findViewById<SwitchMaterial>(R.id.encryptEnableSwitch)
        val encryptRuleIndexInput = view.findViewById<TextInputEditText>(R.id.encryptRuleIndexInput)
        val encryptRulesPreview = view.findViewById<MaterialTextView>(R.id.encryptRulesPreview)

        fun renderRules() {
            val rules = host.currentConfig().encryptRules
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
            val body = if (lines.isEmpty()) getString(R.string.encrypt_rules_empty) else lines.joinToString("\n")
            encryptRulesPreview.text = getString(R.string.encrypt_rules_preview, body)
        }

        fun buildRuleFromForm(): EncryptRule? {
            val path = encryptPathInput.text?.toString()?.trim().orEmpty()
            val password = encryptPasswordInput.text?.toString()?.trim().orEmpty()
            val encType = encryptTypeInput.text?.toString()?.trim().orEmpty().ifBlank { "aes-ctr" }
            if (path.isBlank() || password.isBlank()) return null
            if (encType != "aes-ctr" && encType != "rc4md5") return null
            return EncryptRule(
                path = path,
                password = password,
                encType = encType,
                encName = encryptNameSwitch.isChecked,
                enable = encryptEnableSwitch.isChecked
            )
        }

        fun applyNext(nextRules: List<EncryptRule>) {
            val next = host.currentConfig().copy(encryptRules = nextRules)
            val diff = host.previewChangedKeys(next)
            if (diff.isEmpty()) {
                Snackbar.make(view, getString(R.string.no_changes), Snackbar.LENGTH_SHORT).show()
                return
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.diff_preview_title)
                .setMessage(diff.take(10).joinToString("\n"))
                .setPositiveButton(R.string.confirm_apply) { _, _ ->
                    val result = host.applyConfig(next)
                    Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                    renderRules()
                }
                .setNegativeButton(R.string.cancel_apply, null)
                .show()
        }

        encryptTypeInput.setText("aes-ctr")
        encryptEnableSwitch.isChecked = true
        renderRules()

        view.findViewById<View>(R.id.encryptAddRuleButton).setOnClickListener {
            val newRule = buildRuleFromForm()
            if (newRule == null) {
                Snackbar.make(
                    view,
                    getString(R.string.save_failed_prefix, getString(R.string.encrypt_required_error)),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            applyNext(host.currentConfig().encryptRules + newRule)
        }

        view.findViewById<View>(R.id.encryptUpdateRuleButton).setOnClickListener {
            val rules = host.currentConfig().encryptRules.toMutableList()
            val oneBased = encryptRuleIndexInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val index = (oneBased ?: 0) - 1
            if (index !in rules.indices) {
                Snackbar.make(view, getString(R.string.encrypt_index_error), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updated = buildRuleFromForm()
            if (updated == null) {
                Snackbar.make(
                    view,
                    getString(R.string.save_failed_prefix, getString(R.string.encrypt_required_error)),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            rules[index] = updated
            applyNext(rules)
        }

        view.findViewById<View>(R.id.encryptDeleteRuleButton).setOnClickListener {
            val rules = host.currentConfig().encryptRules.toMutableList()
            val oneBased = encryptRuleIndexInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val index = (oneBased ?: 0) - 1
            if (index !in rules.indices) {
                Snackbar.make(view, getString(R.string.encrypt_index_error), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            rules.removeAt(index)
            applyNext(rules)
        }

        view.findViewById<View>(R.id.encryptClearRulesButton).setOnClickListener {
            applyNext(emptyList())
        }
    }
}
