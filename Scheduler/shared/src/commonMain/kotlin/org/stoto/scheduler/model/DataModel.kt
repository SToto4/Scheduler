package org.stoto.scheduler.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

data class Worker(
    val id: String,
    val name: String,
    val maxShifts: Int = 5,
    val unavailableDates: Set<LocalDate> = emptySet(),
    val preferredDates: Set<LocalDate> = emptySet()
)

data class Shift(
    val date: LocalDate,
    val assignedWorkerId: String? = null
) {
    val isWeekend: Boolean
        get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}

data class Schedule(
    val year: Int,
    val month: Int,
    val shifts: List<Shift> = emptyList()
)