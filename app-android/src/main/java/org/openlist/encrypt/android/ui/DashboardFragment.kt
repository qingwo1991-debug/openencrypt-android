package org.openlist.encrypt.android.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import org.openlist.encrypt.android.R

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? MainUiHost ?: return
        val summary = view.findViewById<MaterialTextView>(R.id.dashboardSummary)
        summary.text = getString(R.string.page_dashboard_summary)

        view.findViewById<MaterialButton>(R.id.startRuntime).setOnClickListener {
            val result = host.requestRuntimeStart()
            Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.stopRuntime).setOnClickListener {
            val result = host.requestRuntimeStop()
            Snackbar.make(view, result.message, Snackbar.LENGTH_SHORT).show()
        }
    }
}
