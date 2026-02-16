package org.openlist.encrypt.android.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.openlist.encrypt.android.R

class TasksFragment : Fragment(R.layout.fragment_tasks) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? MainUiHost ?: return
        val config = host.currentConfig()

        val tasksWebdavSwitch = view.findViewById<SwitchMaterial>(R.id.tasksWebdavSwitch)
        val tasksHeaderTimeoutInput = view.findViewById<TextInputEditText>(R.id.tasksHeaderTimeoutInput)
        val tasksReadIdleInput = view.findViewById<TextInputEditText>(R.id.tasksReadIdleInput)
        val tasksProbeBudgetInput = view.findViewById<TextInputEditText>(R.id.tasksProbeBudgetInput)

        tasksWebdavSwitch.isChecked = config.webdav.enable
        tasksHeaderTimeoutInput.setText(config.webdav.headerTimeoutMs.toString())
        tasksReadIdleInput.setText(config.webdav.readIdleTimeoutMs.toString())
        tasksProbeBudgetInput.setText(config.runtime.probeBudgetListMs.toString())

        view.findViewById<View>(R.id.tasksSaveButton).setOnClickListener {
            val header = tasksHeaderTimeoutInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val idle = tasksReadIdleInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            val budget = tasksProbeBudgetInput.text?.toString()?.trim().orEmpty().toIntOrNull()
            if (header == null || idle == null || budget == null) {
                Snackbar.make(view, getString(R.string.invalid_number), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val current = host.currentConfig()
            val next = current.copy(
                webdav = current.webdav.copy(
                    enable = tasksWebdavSwitch.isChecked,
                    headerTimeoutMs = header,
                    readIdleTimeoutMs = idle
                ),
                runtime = current.runtime.copy(
                    probeBudgetListMs = budget
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
