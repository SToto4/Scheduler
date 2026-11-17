package org.stoto.scheduler.storage

/**
 * Platformfüggetlen fájlművelet-absztrakció szöveges tartalomhoz (UTF-8).
 *
 * A tényleges megvalósítás platformonként eltér (Desktop: natív fájlválasztó és
 * [java.io.File]; Android: a megadott útvonalra írás). A közös kód csak ezt az
 * interfészt és a [defaultFileStorage] gyárat ismeri.
 */
interface FileStorage {

    /** UTF-8 szöveg kiírása a megadott abszolút útvonalra. */
    fun writeText(path: String, content: String)

    /** UTF-8 szöveg beolvasása a megadott abszolút útvonalról. */
    fun readText(path: String): String

    /**
     * Natív "Mentés másként" dialógus (ahol támogatott).
     *
     * @return a felhasználó által választott útvonal, vagy `null`, ha megszakította,
     *         illetve ha a platform nem támogat interaktív dialógust.
     */
    fun chooseSavePath(suggestedName: String): String?

    /**
     * Natív "Megnyitás" dialógus (ahol támogatott).
     *
     * @return a kiválasztott útvonal, vagy `null` ha nincs kiválasztás / nincs dialógustámogatás.
     */
    fun chooseOpenPath(): String?
}

/** A platform alapértelmezett [FileStorage] megvalósítását adja vissza. */
expect fun defaultFileStorage(): FileStorage
