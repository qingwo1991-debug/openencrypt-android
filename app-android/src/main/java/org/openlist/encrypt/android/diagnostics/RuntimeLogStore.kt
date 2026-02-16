package org.openlist.encrypt.android.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RuntimeLogStore(private val context: Context) {
    private val root = File(context.filesDir, "openencrypt/logs")
    private val exportDir = File(context.filesDir, "openencrypt/exports")

    fun openListLogFile(): File = ensure(root).resolve("openlist-runtime.log")

    fun gatewayLogFile(): File = ensure(root).resolve("gateway-runtime.log")

    fun appLogFile(): File = ensure(root).resolve("app-runtime.log")

    fun appendApp(event: String, detail: String) {
        val line = "${now()} APP $event | $detail\n"
        appLogFile().appendText(line)
    }

    fun mergedTail(maxLinesPerFile: Int = 120): String {
        return merged(maxLinesPerFile)
    }

    fun mergedForExport(): String {
        return merged(maxLinesPerFile = null)
    }

    private fun merged(maxLinesPerFile: Int?): String {
        val sections = listOf(
            "APP" to appLogFile(),
            "OPENLIST" to openListLogFile(),
            "GATEWAY" to gatewayLogFile()
        )
        return sections.joinToString(separator = "\n\n") { (name, file) ->
            val lines = if (!file.exists()) {
                listOf("(empty)")
            } else {
                val all = file.readLines()
                if (maxLinesPerFile == null) all else all.takeLast(maxLinesPerFile)
            }
            "===== $name =====\n" + lines.joinToString("\n")
        }
    }

    fun exportMerged(): File {
        val outDir = ensure(exportDir)
        val file = outDir.resolve("openencrypt-log-export-${stamp()}.txt")
        file.writeText(mergedForExport())
        return file
    }

    private fun ensure(dir: File): File {
        dir.mkdirs()
        return dir
    }

    private fun now(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
    }

    private fun stamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
}
