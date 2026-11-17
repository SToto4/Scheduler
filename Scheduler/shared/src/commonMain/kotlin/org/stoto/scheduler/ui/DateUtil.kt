package org.stoto.scheduler.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** Dátum- és naptár-segédek a felülethez (magyar nyelvű címkékkel). */
object DateUtil {

    val monthNames: List<String> = listOf(
        "Január", "Február", "Március", "Április", "Május", "Június",
        "Július", "Augusztus", "Szeptember", "Október", "November", "December"
    )

    /** Rövid napnevek hétfőtől vasárnapig (naptárfejléchez). */
    val shortDayNames: List<String> = listOf("H", "K", "Sze", "Cs", "P", "Szo", "V")

    private val dayNames: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "Hétfő",
        DayOfWeek.TUESDAY to "Kedd",
        DayOfWeek.WEDNESDAY to "Szerda",
        DayOfWeek.THURSDAY to "Csütörtök",
        DayOfWeek.FRIDAY to "Péntek",
        DayOfWeek.SATURDAY to "Szombat",
        DayOfWeek.SUNDAY to "Vasárnap"
    )

    fun monthName(month: Int): String = monthNames.getOrElse(month - 1) { month.toString() }

    fun dayName(date: LocalDate): String = dayNames[date.dayOfWeek] ?: date.dayOfWeek.name

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** Egy dátum rövid formája: ÉÉÉÉ-HH-NN. */
    fun isoDate(date: LocalDate): String {
        val mm = date.monthNumber.toString().padStart(2, '0')
        val dd = date.dayOfMonth.toString().padStart(2, '0')
        return "${date.year}-$mm-$dd"
    }

    /**
     * A hónap napjainak listája [LocalDate]-ként.
     */
    fun daysOfMonth(year: Int, month: Int): List<LocalDate> {
        val days = daysInMonth(year, month)
        return (1..days).map { LocalDate(year, month, it) }
    }

    /**
     * Hány üres cella kell a naptárrács elején, hogy a hónap 1. napja a helyes
     * oszlopba kerüljön (hétfővel kezdődő hét). 0..6.
     */
    fun leadingBlanks(year: Int, month: Int): Int {
        // kotlinx-datetime: DayOfWeek.isoDayNumber 1=hétfő ... 7=vasárnap
        return LocalDate(year, month, 1).dayOfWeek.isoDayNumber - 1
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 30
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
