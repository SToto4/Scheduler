package org.stoto.scheduler.storage

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop (JVM) [FileStorage]: valódi fájl I/O [File]-lal és natív Swing fájlválasztóval.
 */
class DesktopFileStorage : FileStorage {

    override fun writeText(path: String, content: String) {
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
    }

    override fun readText(path: String): String =
        File(path).readText(Charsets.UTF_8)

    override fun chooseSavePath(suggestedName: String): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Mentés másként"
            selectedFile = File(suggestedName)
            applyExtensionFilter(suggestedName)
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.absolutePath
    }

    override fun chooseOpenPath(): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Megnyitás"
            fileFilter = FileNameExtensionFilter("JSON és CSV fájlok", "json", "csv")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.absolutePath
    }

    private fun JFileChooser.applyExtensionFilter(suggestedName: String) {
        val ext = suggestedName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter("$ext fájl", ext)
        }
    }
}

actual fun defaultFileStorage(): FileStorage = DesktopFileStorage()
