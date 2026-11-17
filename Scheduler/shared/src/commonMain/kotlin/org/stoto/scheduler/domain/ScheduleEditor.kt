package org.stoto.scheduler.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Shift
import org.stoto.scheduler.model.Worker

/**
 * A manuális szerkesztéshez tartozó üzleti logika: egy nap átosztása [reassignShift],
 * két nap cseréje [swapShifts], valamint a kemény szabályok ellenőrzése [validateSchedule].
 *
 * Az osztály tiszta (állapotmentes) műveleteket ad: minden módosító függvény új,
 * immutable [Schedule] példányt térít vissza, az eredetit nem bántja.
 */
class ScheduleEditor {

    /** A szabálysértés típusa. */
    enum class ViolationType {
        /** Egymást követő napokon ügyel ugyanaz a dolgozó (hónapfordulót is beleértve). */
        CONSECUTIVE_DAYS,

        /** Olyan napra van beosztva, amelyet kieső/szabadság napként jelölt meg. */
        UNAVAILABLE_DATE,

        /** A dolgozó havi maximuma (maxShifts) túllépve. */
        MAX_SHIFTS_EXCEEDED,

        /** A beosztásban ismeretlen dolgozó-azonosító szerepel. */
        UNKNOWN_WORKER,

        /** Ugyanarra a napra több műszak is szerepel. */
        DUPLICATE_DAY
    }

    /** Egy konkrét szabálysértés leírása, emberi nyelvű üzenettel. */
    data class Violation(
        val type: ViolationType,
        val date: LocalDate?,
        val workerId: String?,
        val message: String
    )

    /**
     * Átosztja az adott nap ügyeletét a megadott dolgozóra (vagy `null`-ra = üres nap).
     *
     * @return új [Schedule] a módosított nappal. Ha a dátum nincs a beosztásban, az eredetit adja vissza.
     */
    fun reassignShift(schedule: Schedule, date: LocalDate, newWorkerId: String?): Schedule {
        if (schedule.shifts.none { it.date == date }) return schedule
        val updated = schedule.shifts.map { shift ->
            if (shift.date == date) shift.copy(assignedWorkerId = newWorkerId) else shift
        }
        return schedule.copy(shifts = updated)
    }

    /**
     * Megcseréli két nap ügyeletesét.
     *
     * @return új [Schedule] a cserével. Ha bármelyik dátum hiányzik, az eredetit adja vissza.
     */
    fun swapShifts(schedule: Schedule, dateA: LocalDate, dateB: LocalDate): Schedule {
        if (dateA == dateB) return schedule
        val shiftA = schedule.shifts.firstOrNull { it.date == dateA } ?: return schedule
        val shiftB = schedule.shifts.firstOrNull { it.date == dateB } ?: return schedule

        val updated = schedule.shifts.map { shift ->
            when (shift.date) {
                dateA -> shift.copy(assignedWorkerId = shiftB.assignedWorkerId)
                dateB -> shift.copy(assignedWorkerId = shiftA.assignedWorkerId)
                else -> shift
            }
        }
        return schedule.copy(shifts = updated)
    }

    /**
     * Ellenőrzi, hogy egy adott nap átosztása okozna-e kemény szabálysértést,
     * anélkül, hogy a beosztást ténylegesen módosítaná. Gyors előzetes vizsgálat a UI-nak.
     *
     * @param previousMonthLastWorkerId az előző hónap utolsó napjának ügyeletese (hónapforduló-ellenőrzéshez).
     * @param nextMonthFirstWorkerId a következő hónap első napjának ügyeletese (hónapvég-ellenőrzéshez).
     */
    fun validateReassignment(
        schedule: Schedule,
        workers: List<Worker>,
        date: LocalDate,
        newWorkerId: String?,
        previousMonthLastWorkerId: String? = null,
        nextMonthFirstWorkerId: String? = null
    ): List<Violation> {
        val candidate = reassignShift(schedule, date, newWorkerId)
        return validateSchedule(candidate, workers, previousMonthLastWorkerId, nextMonthFirstWorkerId)
            .filter { it.date == date || it.workerId == newWorkerId }
    }

    /**
     * Teljes beosztás ellenőrzése a kemény szabályok mentén.
     *
     * @return az összes talált szabálysértés listája (üres lista = szabályos beosztás).
     */
    fun validateSchedule(
        schedule: Schedule,
        workers: List<Worker>,
        previousMonthLastWorkerId: String? = null,
        nextMonthFirstWorkerId: String? = null
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val workersById = workers.associateBy { it.id }
        val byDate = schedule.shifts.groupBy { it.date }

        // Duplikált nap ellenőrzése.
        byDate.filterValues { it.size > 1 }.keys.forEach { date ->
            violations += Violation(
                type = ViolationType.DUPLICATE_DAY,
                date = date,
                workerId = null,
                message = "Több műszak is szerepel erre a napra: $date."
            )
        }

        // Dolgozónkénti havi összesítés a maxShifts-hez.
        val shiftCountByWorker = schedule.shifts
            .mapNotNull { it.assignedWorkerId }
            .groupingBy { it }
            .eachCount()

        for (shift in schedule.shifts) {
            val workerId = shift.assignedWorkerId ?: continue
            val worker = workersById[workerId]

            if (worker == null) {
                violations += Violation(
                    type = ViolationType.UNKNOWN_WORKER,
                    date = shift.date,
                    workerId = workerId,
                    message = "Ismeretlen dolgozó ($workerId) a(z) ${shift.date} napon."
                )
                continue
            }

            // Kieső / szabadság nap.
            if (shift.date in worker.unavailableDates) {
                violations += Violation(
                    type = ViolationType.UNAVAILABLE_DATE,
                    date = shift.date,
                    workerId = workerId,
                    message = "${worker.name} nem elérhető ekkor: ${shift.date}."
                )
            }
        }

        // Havi maximum túllépése.
        for ((workerId, count) in shiftCountByWorker) {
            val worker = workersById[workerId] ?: continue
            if (count > worker.maxShifts) {
                violations += Violation(
                    type = ViolationType.MAX_SHIFTS_EXCEEDED,
                    date = null,
                    workerId = workerId,
                    message = "${worker.name} havi maximuma túllépve: $count / ${worker.maxShifts}."
                )
            }
        }

        // Egymást követő napok (a hónapon belül + a hónapfordulók).
        for (shift in schedule.shifts) {
            val workerId = shift.assignedWorkerId ?: continue
            val previousDay = shift.date.minus(DatePeriod(days = 1))

            val previousWorkerId = byDate[previousDay]?.firstOrNull()?.assignedWorkerId
                ?: if (isFirstDayOfMonth(shift.date, schedule)) previousMonthLastWorkerId else null

            if (previousWorkerId != null && previousWorkerId == workerId) {
                val name = workersById[workerId]?.name ?: workerId
                violations += Violation(
                    type = ViolationType.CONSECUTIVE_DAYS,
                    date = shift.date,
                    workerId = workerId,
                    message = "$name egymást követő napokon ügyel: $previousDay és ${shift.date}."
                )
            }
        }

        // Hónap utolsó napja és a következő hónap első napja közti ütközés.
        val lastShift = schedule.shifts.maxByOrNull { it.date }
        if (lastShift != null && nextMonthFirstWorkerId != null &&
            lastShift.assignedWorkerId != null &&
            lastShift.assignedWorkerId == nextMonthFirstWorkerId
        ) {
            val name = workersById[nextMonthFirstWorkerId]?.name ?: nextMonthFirstWorkerId
            violations += Violation(
                type = ViolationType.CONSECUTIVE_DAYS,
                date = lastShift.date.plus(DatePeriod(days = 1)),
                workerId = nextMonthFirstWorkerId,
                message = "$name a hónap utolsó és a következő hónap első napján is ügyelne."
            )
        }

        return violations
    }

    private fun isFirstDayOfMonth(date: LocalDate, schedule: Schedule): Boolean =
        date.year == schedule.year && date.monthNumber == schedule.month && date.dayOfMonth == 1
}
