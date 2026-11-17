package org.stoto.scheduler.storage

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * Desktop (JVM) [FileStorage]: valódi fájl I/O [File]-lal és modern, natív
 * [FileDialog]-alapú fájlválasztóval. A [FileDialog] Windows és macOS alatt is az
 * operációs rendszerbe épített natív fájlkezelő ablakot nyitja meg (szemben a régi
 * Swing [javax.swing.JFileChooser]-rel).
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
        val dialog = FileDialog(null as Frame?, "Mentés másként", FileDialog.SAVE).apply {
            file = suggestedName
            filenameFilter = extensionFilter(suggestedName)
            isVisible = true
        }
        return selectedPath(dialog)
    }

    override fun chooseOpenPath(): String? {
        val dialog = FileDialog(null as Frame?, "Megnyitás", FileDialog.LOAD).apply {
            filenameFilter = FilenameFilter { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".json") || lower.endsWith(".csv")
            }
            isVisible = true
        }
        return selectedPath(dialog)
    }

    /** A dialógus `directory` + `file` összefűzése teljes útvonallá (vagy `null`, ha megszakítva). */
    private fun selectedPath(dialog: FileDialog): String? {
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name).absolutePath
    }

    /** A javasolt fájlnév kiterjesztéséből képzett szűrő (ha van kiterjesztés). */
    private fun extensionFilter(suggestedName: String): FilenameFilter? {
        val ext = suggestedName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return FilenameFilter { _, name -> name.lowercase().endsWith(".$ext") }
    }
}

actual fun defaultFileStorage(): FileStorage = DesktopFileStorage()
