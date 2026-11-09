package org.stoto.scheduler.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Shift
import org.stoto.scheduler.model.Worker

class ShiftScheduler {

    /**
     * Legenerálja a havi beosztást Backtracking algoritmussal.
     * @param previousMonthLastWorkerId Opcionális: az előző hónap utolsó napján ügyelő dolgozó azonosítója.
     * @return Schedule ha sikerült minden napot lefedni, vagy null, ha a megkötések miatt nincs érvényes megoldás.
     */
    fun generateSchedule(
        year: Int,
        month: Int,
        workers: List<Worker>,
        previousMonthLastWorkerId: String? = null
    ): Schedule? {
        val daysInMonth = getDaysInMonth(year, month)
        val assignedShifts = mutableListOf<Shift>()

        val success = backtrack(
            dayIndex = 0,
            days = daysInMonth,
            workers = workers,
            currentShifts = assignedShifts,
            previousMonthLastWorkerId = previousMonthLastWorkerId
        )

        return if (success) {
            Schedule(year = year, month = month, shifts = assignedShifts)
        } else {
            null // Nem létezik olyan beosztás, ami minden szabályt kielégít
        }
    }

    /**
     * Rekurzív visszalépéses keresés.
     */
    private fun backtrack(
        dayIndex: Int,
        days: List<LocalDate>,
        workers: List<Worker>,
        currentShifts: MutableList<Shift>,
        previousMonthLastWorkerId: String?
    ): Boolean {
        // Alapeset: sikeresen beosztottuk a hónap összes napját
        if (dayIndex >= days.size) {
            return true
        }

        val currentDate = days[dayIndex]
        val eligibleWorkers = getEligibleWorkers(
            date = currentDate,
            workers = workers,
            currentShifts = currentShifts,
            previousMonthLastWorkerId = previousMonthLastWorkerId
        )

        // Rendezzük a jelölteket pontszám szerint növekvő sorrendbe (legjobb legelöl, majd a 2., 3...)
        val rankedCandidates = eligibleWorkers
            .shuffled() // Holtverseny feloldása véletlenszerűséggel
            .sortedBy { worker -> calculatePenaltyScore(worker, currentDate, currentShifts) }

        // Próbáljuk ki a jelölteket sorban
        for (candidate in rankedCandidates) {
            val shift = Shift(date = currentDate, assignedWorkerId = candidate.id)
            currentShifts.add(shift)

            // Továbblépünk a következő napra
            if (backtrack(dayIndex + 1, days, workers, currentShifts, previousMonthLastWorkerId)) {
                return true
            }

            // VISSZALÉPÉS (Zsákutcába futottunk a jövőbeli napokon): visszavonjuk a döntést
            currentShifts.removeAt(currentShifts.lastIndex)
        }

        // Egyik jelölttel sem lehetett érvényes havat kirakni innen kiindulva
        return false
    }

    /**
     * Kiszűri azokat a dolgozókat, akik a KEMÉNY SZABÁLYOK (Hard Constraints) alapján
     * egyáltalán beoszthatók az adott napra.
     */
    private fun getEligibleWorkers(
        date: LocalDate,
        workers: List<Worker>,
        currentShifts: List<Shift>,
        previousMonthLastWorkerId: String?
    ): List<Worker> {
        val previousDay = date.minus(DatePeriod(days = 1))

        // Előző napi dolgozó megkeresése (ha a hónap 1. napja, akkor az előző havi paraméterből)
        val previousWorkerId = if (date.dayOfMonth == 1) {
            previousMonthLastWorkerId
        } else {
            currentShifts.find { it.date == previousDay }?.assignedWorkerId
        }

        return workers.filter { worker ->
            // 1. Nem szabadságos / kieső
            val isAvailable = date !in worker.unavailableDates

            // 2. Még van szabad havi kerete
            val currentCount = currentShifts.count { it.assignedWorkerId == worker.id }
            val hasRemainingShifts = currentCount < worker.maxShifts

            // 3. Nem ő ügyelt tegnap
            val workedYesterday = worker.id == previousWorkerId

            isAvailable && hasRemainingShifts && !workedYesterday
        }
    }

    /**
     * A szabályos jelöltek közül kiválasztja a legoptimálisabbat (Soft Constraints / pontozás).
     */
    private fun selectBestWorker(
        date: LocalDate,
        candidates: List<Worker>,
        currentShifts: List<Shift>
    ): Worker? {
        return candidates
            .shuffled() //holtverseny esetén véletlenszerű
            .minByOrNull { worker ->
            calculatePenaltyScore(worker, date, currentShifts)
        }
    }

    /**
     * Pontszámítás a rendezéshez: minél kisebb a pont, annál alkalmasabb a jelölt.
     */
    private fun calculatePenaltyScore(
        worker: Worker,
        date: LocalDate,
        currentShifts: List<Shift>
    ): Int {
        var score = 0

        // 1. Eddigi összes műszak száma (egyensúly tartása)
        val totalShiftsSoFar = currentShifts.count { it.assignedWorkerId == worker.id }
        score += totalShiftsSoFar * 10

        // 2. Preferencia figyelembevétele (ha kifejezetten kérte ezt a napot, nagy előny jár)
        if (date in worker.preferredDates) {
            score -= 25
        }

        // 3. Hétvégi igazságosság
        val isTargetWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        if (isTargetWeekend) {
            val weekendShiftsSoFar = currentShifts.count { shift ->
                shift.assignedWorkerId == worker.id && shift.isWeekend
            }
            score += weekendShiftsSoFar * 15
        }

        return score
    }

    /**
     * Segédfüggvény: előállítja a megadott hónap összes napját LocalDate listaként.
     */
    private fun getDaysInMonth(year: Int, month: Int): List<LocalDate> {
        // 1. Kiszámoljuk a következő hónap évét és hónapját
        val nextYear = if (month == 12) year + 1 else year
        val nextMonth = if (month == 12) 1 else month + 1

        // 2. A következő hónap 1. napjából visszalépünk 1 napot -> ez az adott hónap utolsó napja
        val firstOfNextMonth = LocalDate(nextYear, nextMonth, 1)
        val lastDayOfMonth = firstOfNextMonth.minus(DatePeriod(days = 1))

        // 3. 1-től az utolsó nap sorszámáig legeneráljuk a LocalDate listát
        return (1..lastDayOfMonth.dayOfMonth).map { day ->
            LocalDate(year, month, day)
        }
    }
}