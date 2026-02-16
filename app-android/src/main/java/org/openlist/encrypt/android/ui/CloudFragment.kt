package org.openlist.encrypt.android.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.openlist.encrypt.android.R

class CloudFragment : Fragment(R.layout.fragment_cloud) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? MainUiHost ?: return
        val config = host.currentConfig()

        val cloudHostInput = view.findViewById<TextInputEditText>(R.id.cloudHostInput)
        val cloudPortInput = view.findViewById<TextInputEditText>(R.id.cloudPortInput)
        val cloudHttpsSwitch = view.findViewById<SwitchMaterial>(R.id.cloudHttpsSwitch)
        val cloudH2cSwitch = view.findViewById<SwitchMaterial>(R.id.cloudH2cSwitch)

        cloudHostInput.setText(config.openlist.host)
        cloudPortInput.setText(config.openlist.port.toString())
        cloudHttpsSwitch.isChecked = config.openlist.https
        cloudH2cSwitch.isChecked = config.openlist.h2c

        view.findViewById<View>(R.id.cloudSaveButton).setOnClickListener {
            val hostValue = cloudHostInput.text?.toString()?.trim().orEmpty().ifBlank { "127.0.0.1" }
            val port = cloudPortInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (port == null) {
                Snackbar.make(view, getString(R.string.invalid_number), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val next = host.currentConfig().copy(
                openlist = host.currentConfig().openlist.copy(
                    host = hostValue,
                    port = port,
                    https = cloudHttpsSwitch.isChecked,
                    h2c = cloudH2cSwitch.isChecked
                )
            )
            val diff = host.previewChangedKeys(next)
            if (diff.isEmpty()) {
                Snackbar.make(view, getString(R.string.no_changes), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.diff_preview_title)
                .setMessage(diff.take(10).joinToString("\n"))
                .setPositiveButton(R.string.confirm_apply) { _, _ ->
                    val result = host.applyConfig(next)
                    Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel_apply, null)
                .show()
        }
    }
}
