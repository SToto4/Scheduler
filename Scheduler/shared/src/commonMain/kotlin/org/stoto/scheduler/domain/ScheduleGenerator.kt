package org.stoto.scheduler.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Shift
import org.stoto.scheduler.model.Worker

/**
 * Havi ügyeleti beosztást generáló motor.
 *
 * A keresés visszalépéses (backtracking) algoritmussal dolgozik, a kemény szabályok
 * (szabadság, havi maximum, egymást követő napok tiltása – a hónapfordulót is beleértve)
 * mentén szűri a jelölteket, a puha feltételeket (arányos teher, hétvége-elosztás,
 * preferált napok) pedig pontozással veszi figyelembe.
 *
 * Ha a szigorú havi maximummal nincs megoldás, iteratívan lazítja a keretet
 * (lásd [maxAllowedOvertime]), így végszükség esetén is ad eredményt.
 */
class ShiftScheduler(
    /** A backtracking által bejárható csomópontok (próbálkozások) felső korlátja. */
    private val maxBacktrackSteps: Int = 2_000_000,
    /** A havi maximum túllépésének felső határa a relaxáció során. */
    private val maxOvertimeRelaxation: Int = 10
) {

    /** A generálás kimenetele – siker esetén a beosztás, egyébként a hiba oka. */
    sealed interface GenerationResult {
        data class Success(
            val schedule: Schedule,
            /** Hány egységnyi túlórát kellett engedélyezni a megoldáshoz (0 = szigorú keret). */
            val appliedOvertime: Int
        ) : GenerationResult

        data class Failure(val reason: String) : GenerationResult
    }

    /**
     * Legenerálja a havi beosztást.
     *
     * @param previousMonthLastWorkerId Opcionális: az előző hónap utolsó napján ügyelő dolgozó.
     * @return [Schedule] ha sikerült minden napot lefedni, különben `null`.
     *         Részletesebb kimenetért lásd [generate].
     */
    fun generateSchedule(
        year: Int,
        month: Int,
        workers: List<Worker>,
        previousMonthLastWorkerId: String? = null
    ): Schedule? = (generate(year, month, workers, previousMonthLastWorkerId)
        as? GenerationResult.Success)?.schedule

    /**
     * Mint a [generateSchedule], de strukturált eredményt ad vissza (siker + túlóra, vagy hiba).
     */
    fun generate(
        year: Int,
        month: Int,
        workers: List<Worker>,
        previousMonthLastWorkerId: String? = null
    ): GenerationResult {
        // --- Bemenet validálása (szélsőértékek) ---
        if (month !in 1..12) {
            return GenerationResult.Failure("Érvénytelen hónap: $month (1–12 várt).")
        }
        if (year !in 1..9999) {
            return GenerationResult.Failure("Érvénytelen év: $year.")
        }

        val days = getDaysInMonth(year, month)

        if (workers.isEmpty()) {
            return GenerationResult.Failure("Nincs megadva egyetlen dolgozó sem.")
        }
        // Egyedi azonosítók – duplikátum az egész logikát elrontaná.
        val duplicateIds = workers.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            return GenerationResult.Failure("Ismétlődő dolgozó-azonosító(k): ${duplicateIds.joinToString()}")
        }

        // --- Iteratív relaxáció: először szigorú kerettel, majd fokozatosan túlórát engedve ---
        for (overtime in 0..maxOvertimeRelaxation) {
            val assigned = mutableListOf<Shift>()
            val steps = IntRef()
            val outcome = backtrack(
                dayIndex = 0,
                days = days,
                workers = workers,
                currentShifts = assigned,
                previousMonthLastWorkerId = previousMonthLastWorkerId,
                allowedOvertime = overtime,
                steps = steps
            )
            when (outcome) {
                SearchOutcome.FOUND ->
                    return GenerationResult.Success(
                        Schedule(year = year, month = month, shifts = assigned.sortedBy { it.date }),
                        appliedOvertime = overtime
                    )
                SearchOutcome.STEP_LIMIT ->
                    return GenerationResult.Failure(
                        "A keresés elérte a lépéskorlátot (${maxBacktrackSteps}). " +
                            "Próbáld csökkenteni a megkötéseket vagy növelni a dolgozók számát."
                    )
                SearchOutcome.EXHAUSTED -> {
                    // Ezzel a túlóra-kerettel nincs megoldás; próbáljuk a következő szinttel.
                }
            }
        }

        return GenerationResult.Failure(
            "Nem található érvényes beosztás még ${maxOvertimeRelaxation} túlóra engedélyezésével sem. " +
                "Túl sok a kieső nap, vagy túl alacsony a havi maximum."
        )
    }

    private enum class SearchOutcome { FOUND, EXHAUSTED, STEP_LIMIT }

    private class IntRef(var value: Int = 0)

    /**
     * Rekurzív visszalépéses keresés lépéskorláttal és túlóra-kerettel.
     */
    private fun backtrack(
        dayIndex: Int,
        days: List<LocalDate>,
        workers: List<Worker>,
        currentShifts: MutableList<Shift>,
        previousMonthLastWorkerId: String?,
        allowedOvertime: Int,
        steps: IntRef
    ): SearchOutcome {
        // Alapeset: minden napot beosztottunk.
        if (dayIndex >= days.size) return SearchOutcome.FOUND

        if (steps.value >= maxBacktrackSteps) return SearchOutcome.STEP_LIMIT
        steps.value++

        val currentDate = days[dayIndex]
        val eligibleWorkers = getEligibleWorkers(
            date = currentDate,
            workers = workers,
            currentShifts = currentShifts,
            previousMonthLastWorkerId = previousMonthLastWorkerId,
            allowedOvertime = allowedOvertime
        )

        // Jelöltek rendezése pontszám szerint (legjobb elöl), holtverseny feloldása véletlennel.
        val rankedCandidates = eligibleWorkers
            .shuffled()
            .sortedBy { worker -> calculatePenaltyScore(worker, currentDate, currentShifts) }

        for (candidate in rankedCandidates) {
            currentShifts.add(Shift(date = currentDate, assignedWorkerId = candidate.id))

            val result = backtrack(
                dayIndex + 1, days, workers, currentShifts,
                previousMonthLastWorkerId, allowedOvertime, steps
            )
            if (result == SearchOutcome.FOUND) return SearchOutcome.FOUND
            if (result == SearchOutcome.STEP_LIMIT) {
                currentShifts.removeAt(currentShifts.lastIndex)
                return SearchOutcome.STEP_LIMIT
            }

            // Visszalépés: zsákutca a jövőbeli napokon.
            currentShifts.removeAt(currentShifts.lastIndex)
        }

        return SearchOutcome.EXHAUSTED
    }

    /**
     * A KEMÉNY SZABÁLYOK alapján beosztható dolgozók listája az adott napra.
     */
    private fun getEligibleWorkers(
        date: LocalDate,
        workers: List<Worker>,
        currentShifts: List<Shift>,
        previousMonthLastWorkerId: String?,
        allowedOvertime: Int
    ): List<Worker> {
        val previousDay = date.minus(DatePeriod(days = 1))

        // Előző nap ügyeletese (a hónap 1. napján az előző hónapból átadott érték).
        val previousWorkerId = if (date.dayOfMonth == 1) {
            previousMonthLastWorkerId
        } else {
            currentShifts.find { it.date == previousDay }?.assignedWorkerId
        }

        return workers.filter { worker ->
            val isAvailable = date !in worker.unavailableDates

            val currentCount = currentShifts.count { it.assignedWorkerId == worker.id }
            val hasRemainingShifts = currentCount < worker.maxShifts + allowedOvertime

            val workedYesterday = worker.id == previousWorkerId

            isAvailable && hasRemainingShifts && !workedYesterday
        }
    }

    /**
     * Pontszámítás a jelöltek rendezéséhez: minél kisebb, annál alkalmasabb.
     */
    private fun calculatePenaltyScore(
        worker: Worker,
        date: LocalDate,
        currentShifts: List<Shift>
    ): Int {
        var score = 0

        // 1. Teher-egyensúly: eddigi műszakok száma.
        val totalShiftsSoFar = currentShifts.count { it.assignedWorkerId == worker.id }
        score += totalShiftsSoFar * 10

        // 2. Preferált nap: erős kedvezmény.
        if (date in worker.preferredDates) {
            score -= 25
        }

        // 3. Hétvégi igazságosság.
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
     * Segédfüggvény: a megadott hónap összes napja [LocalDate] listaként.
     */
    private fun getDaysInMonth(year: Int, month: Int): List<LocalDate> {
        val nextYear = if (month == 12) year + 1 else year
        val nextMonth = if (month == 12) 1 else month + 1

        val firstOfNextMonth = LocalDate(nextYear, nextMonth, 1)
        val lastDayOfMonth = firstOfNextMonth.minus(DatePeriod(days = 1))

        return (1..lastDayOfMonth.dayOfMonth).map { day ->
            LocalDate(year, month, day)
        }
    }
}
