package org.stoto.scheduler.storage

import java.io.File

/**
 * Android [FileStorage]: útvonal-alapú fájl I/O.
 *
 * A natív fájlválasztó Androidon az Storage Access Framework (SAF) Activity-alapú
 * folyamata, amely a UI réteghez tartozik, ezért a [chooseSavePath] / [chooseOpenPath]
 * itt `null`-t ad vissza — a UI ekkor saját SAF-dialógust nyit, és a kiválasztott
 * útvonalat adja át a [writeText] / [readText] hívásoknak.
 */
class AndroidFileStorage : FileStorage {

    override fun writeText(path: String, content: String) {
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
    }

    override fun readText(path: String): String =
        File(path).readText(Charsets.UTF_8)

    override fun chooseSavePath(suggestedName: String): String? = null

    override fun chooseOpenPath(): String? = null
}

actual fun defaultFileStorage(): FileStorage = AndroidFileStorage()
