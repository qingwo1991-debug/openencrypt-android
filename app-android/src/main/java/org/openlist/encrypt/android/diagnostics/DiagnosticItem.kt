package org.openlist.encrypt.android.diagnostics

data class DiagnosticItem(
    val key: String,
    val ok: Boolean,
    val message: String,
    val fixHint: String? = null
)
