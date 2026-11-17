package org.stoto.scheduler.storage

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Worker

/**
 * Excel-kompatibilis CSV szöveget állít elő egy [Schedule] objektumból.
 *
 * Oszlopok: dátum, hét napja, dolgozó neve, hétvége-jelző.
 * Alapértelmezetten pontosvessző a tagoló (ez a magyar/európai Excel-honosítás szokása),
 * és UTF-8 BOM kerül a szöveg elejére, hogy az Excel helyesen jelenítse meg az ékezeteket.
 */
object CsvExporter {

    /** Byte Order Mark, hogy az Excel UTF-8-ként nyissa meg a fájlt. */
    const val UTF8_BOM: String = "\uFEFF"

    private val hungarianDayNames: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "Hétfő",
        DayOfWeek.TUESDAY to "Kedd",
        DayOfWeek.WEDNESDAY to "Szerda",
        DayOfWeek.THURSDAY to "Csütörtök",
        DayOfWeek.FRIDAY to "Péntek",
        DayOfWeek.SATURDAY to "Szombat",
        DayOfWeek.SUNDAY to "Vasárnap"
    )

    /**
     * Elkészíti a CSV szöveget.
     *
     * @param schedule a kész beosztás.
     * @param workers a dolgozók (az azonosító -> név feloldáshoz).
     * @param delimiter mezőelválasztó (alapértelmezés: ';').
     * @param includeBom ha true, a szöveg UTF-8 BOM-mal kezdődik (Excel-barát).
     * @param unassignedLabel üres napra írt szöveg.
     */
    fun export(
        schedule: Schedule,
        workers: List<Worker>,
        delimiter: Char = ';',
        includeBom: Boolean = true,
        unassignedLabel: String = "— nincs beosztva —"
    ): String {
        val nameById = workers.associate { it.id to it.name }
        val sep = delimiter.toString()
        val builder = StringBuilder()

        if (includeBom) builder.append(UTF8_BOM)

        // Fejléc
        builder.append(
            listOf("Dátum", "Nap", "Ügyeletes", "Hétvége")
                .joinToString(sep) { escape(it, delimiter) }
        )
        builder.append("\r\n") // CRLF az Excel kedvéért

        schedule.shifts
            .sortedBy { it.date }
            .forEach { shift ->
                val workerName = shift.assignedWorkerId
                    ?.let { nameById[it] ?: it }
                    ?: unassignedLabel
                val row = listOf(
                    formatDate(shift.date),
                    hungarianDayNames[shift.date.dayOfWeek] ?: shift.date.dayOfWeek.name,
                    workerName,
                    if (shift.isWeekend) "igen" else "nem"
                )
                builder.append(row.joinToString(sep) { escape(it, delimiter) })
                builder.append("\r\n")
            }

        return builder.toString()
    }

    /** ISO dátum (ÉÉÉÉ-HH-NN), nullával feltöltve. */
    private fun formatDate(date: LocalDate): String {
        val mm = date.monthNumber.toString().padStart(2, '0')
        val dd = date.dayOfMonth.toString().padStart(2, '0')
        return "${date.year}-$mm-$dd"
    }

    /**
     * CSV mező escape-elése: ha a mező tartalmazza a tagolót, idézőjelet vagy sortörést,
     * idézőjelek közé kerül, és a belső idézőjelek duplázódnak (RFC 4180).
     */
    private fun escape(value: String, delimiter: Char): String {
        val needsQuoting = value.contains(delimiter) ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        if (!needsQuoting) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
