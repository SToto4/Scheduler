package org.stoto.scheduler.storage

import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Worker

/**
 * Magas szintű homlokzat (facade) a perzisztenciához és exporthoz:
 * összeköti a [JsonStore], [CsvExporter] és [FileStorage] rétegeket.
 *
 * A felsőbb rétegek (ViewModel/UI) ezen keresztül mentenek/töltenek és exportálnak,
 * nem kell ismerniük a szerializáció vagy a fájlrendszer részleteit.
 */
class SchedulerRepository(
    private val fileStorage: FileStorage = defaultFileStorage()
) {

    // --- Dolgozók (JSON) ---

    fun saveWorkers(workers: List<Worker>, path: String) {
        fileStorage.writeText(path, JsonStore.encodeWorkers(workers))
    }

    fun loadWorkers(path: String): List<Worker> =
        JsonStore.decodeWorkers(fileStorage.readText(path))

    // --- Beosztás (JSON) ---

    fun saveSchedule(schedule: Schedule, path: String) {
        fileStorage.writeText(path, JsonStore.encodeSchedule(schedule))
    }

    fun loadSchedule(path: String): Schedule =
        JsonStore.decodeSchedule(fileStorage.readText(path))

    // --- Export (CSV) ---

    fun exportScheduleCsv(
        schedule: Schedule,
        workers: List<Worker>,
        path: String,
        delimiter: Char = ';'
    ) {
        fileStorage.writeText(path, CsvExporter.export(schedule, workers, delimiter))
    }

    // --- Natív dialógusok (ahol támogatott) ---

    fun chooseSavePath(suggestedName: String): String? =
        fileStorage.chooseSavePath(suggestedName)

    fun chooseOpenPath(): String? =
        fileStorage.chooseOpenPath()

    companion object {
        /** Javasolt fájlnév a beosztás CSV exportjához egy adott hónapra. */
        fun defaultCsvName(year: Int, month: Int): String =
            "beosztas_${year}_${month.toString().padStart(2, '0')}.csv"

        /** Javasolt fájlnév a beosztás JSON mentéséhez. */
        fun defaultScheduleJsonName(year: Int, month: Int): String =
            "beosztas_${year}_${month.toString().padStart(2, '0')}.json"

        /** Javasolt fájlnév a dolgozói lista mentéséhez. */
        const val DEFAULT_WORKERS_JSON_NAME: String = "dolgozok.json"
    }
}
