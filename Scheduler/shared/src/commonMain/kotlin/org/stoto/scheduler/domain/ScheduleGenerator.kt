package org.stoto.scheduler.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Shift
import org.stoto.scheduler.model.Worker

/**
 * A beosztás-generálás zárt eredménystruktúrája.
 * A [SchedulerViewModel] pontosan a [Failure.reason] üzenetét jeleníti meg a Snackbarban.
 */
sealed interface ScheduleResult {
    data class Success(val schedule: Schedule) : ScheduleResult
    data class Failure(val reason: String) : ScheduleResult
}

/**
 * Havi ügyeleti beosztást generáló motor.
 *
 * A keresés visszalépéses (backtracking) algoritmussal dolgozik MRV (Minimum Remaining
 * Values) heurisztikával: mindig azt a még ki nem osztott napot választja következőnek,
 * amelyre a legkevesebb szabályos jelölt van. A kemény szabályok (szabadság, havi maximum,
 * egymást követő napok tiltása – a hónapfordulót is beleértve) szűrik a jelölteket, a puha
 * feltételeket (arányos teher, hétvége-elosztás, preferált napok) pontozás veszi figyelembe.
 *
 * Ha a szigorú havi maximummal nincs megoldás, iteratívan lazítja a keretet
 * (lásd [maxOvertimeRelaxation]), így végszükség esetén is ad eredményt.
 */
class ShiftScheduler(
    /** A backtracking által bejárható csomópontok (próbálkozások) felső korlátja. */
    private val maxBacktrackSteps: Int = 100_000,
    /** A havi maximum túllépésének felső határa a relaxáció során. */
    private val maxOvertimeRelaxation: Int = 10
) {

    /**
     * Legenerálja a havi beosztást, és zárt [ScheduleResult]-ot ad vissza.
     *
     * @param previousMonthLastWorkerId Opcionális: az előző hónap utolsó napján ügyelő dolgozó.
     */
    fun generateSchedule(
        year: Int,
        month: Int,
        workers: List<Worker>,
        previousMonthLastWorkerId: String? = null
    ): ScheduleResult {
        // --- Bemenet validálása ---
        if (month !in 1..12) {
            return ScheduleResult.Failure("Érvénytelen hónap: $month (1–12 várt).")
        }
        if (year !in 1..9999) {
            return ScheduleResult.Failure("Érvénytelen év: $year.")
        }
        if (workers.isEmpty()) {
            return ScheduleResult.Failure("Nincs felvett dolgozó.")
        }

        val duplicateIds = workers.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            return ScheduleResult.Failure("Ismétlődő dolgozó-azonosító(k): ${duplicateIds.joinToString()}")
        }

        val days = getDaysInMonth(year, month)
        val dayCount = days.size

        // --- Előzetes kapacitásvizsgálat ---
        // A legnagyobb engedhető kapacitás a teljes relaxáció mellett.
        val maxCapacity = workers.sumOf { it.maxShifts } + workers.size * maxOvertimeRelaxation
        if (maxCapacity < dayCount) {
            val strictCapacity = workers.sumOf { it.maxShifts }
            return ScheduleResult.Failure(
                "Nincs elég kapacitás: a hónap $dayCount napos, de a dolgozók összesen " +
                    "legfeljebb $strictCapacity műszakot tudnak vállalni."
            )
        }

        // --- Iteratív relaxáció: szigorú kerettel indul, majd fokozatosan túlórát enged ---
        var reachedStepLimit = false
        for (overtime in 0..maxOvertimeRelaxation) {
            // Ezen a relaxációs szinten egyáltalán elég-e az összkapacitás?
            val capacityAtLevel = workers.sumOf { it.maxShifts + overtime }
            if (capacityAtLevel < dayCount) continue

            val assignment = HashMap<LocalDate, String>(dayCount * 2)
            val counts = HashMap<String, Int>()
            val steps = IntRef()

            val outcome = backtrack(
                remaining = days.toMutableList(),
                assignment = assignment,
                counts = counts,
                workers = workers,
                allDays = days,
                previousMonthLastWorkerId = previousMonthLastWorkerId,
                allowedOvertime = overtime,
                steps = steps
            )
            when (outcome) {
                SearchOutcome.FOUND -> {
                    val shifts = days.map { date -> Shift(date, assignment[date]) }
                    return ScheduleResult.Success(Schedule(year = year, month = month, shifts = shifts))
                }
                SearchOutcome.STEP_LIMIT -> reachedStepLimit = true
                SearchOutcome.EXHAUSTED -> {
                    // Nincs megoldás ezzel a kerettel; próbáljuk a következő szinttel.
                }
            }
        }

        // A kapacitás elvileg elég, de a szabadságok / pihenőidő miatt nincs megoldás.
        return ScheduleResult.Failure(
            if (reachedStepLimit) {
                "A megadott szabadságok és pihenőidő-szabályok mellett nem sikerült ütközésmentes " +
                    "beosztást találni a megengedett lépésszámon (${maxBacktrackSteps}) belül."
            } else {
                "A megadott szabadságok és pihenőidő-szabályok mellett nem állítható össze " +
                    "ütközésmentes beosztás."
            }
        )
    }

    private enum class SearchOutcome { FOUND, EXHAUSTED, STEP_LIMIT }

    private class IntRef(var value: Int = 0)

    /**
     * Rekurzív visszalépéses keresés MRV heurisztikával és lépéskorláttal.
     *
     * @param remaining a még ki nem osztott napok (módosítható munkalista).
     * @param assignment nap -> dolgozó azonosító (az eddigi döntések).
     * @param counts dolgozó -> eddig kiosztott műszakszám (gyors kerethatár-ellenőrzéshez).
     */
    private fun backtrack(
        remaining: MutableList<LocalDate>,
        assignment: HashMap<LocalDate, String>,
        counts: HashMap<String, Int>,
        workers: List<Worker>,
        allDays: List<LocalDate>,
        previousMonthLastWorkerId: String?,
        allowedOvertime: Int,
        steps: IntRef
    ): SearchOutcome {
        if (remaining.isEmpty()) return SearchOutcome.FOUND

        if (steps.value >= maxBacktrackSteps) return SearchOutcome.STEP_LIMIT
        steps.value++

        // --- MRV: a legkevesebb szabályos jelölttel rendelkező nap kiválasztása ---
        var bestIndex = -1
        var bestCandidates: List<Worker>? = null
        var bestSize = Int.MAX_VALUE
        for (i in remaining.indices) {
            val candidates = eligibleWorkers(
                date = remaining[i],
                workers = workers,
                assignment = assignment,
                counts = counts,
                firstDay = allDays.first(),
                previousMonthLastWorkerId = previousMonthLastWorkerId,
                allowedOvertime = allowedOvertime
            )
            if (candidates.size < bestSize) {
                bestSize = candidates.size
                bestIndex = i
                bestCandidates = candidates
                if (bestSize == 0) break // Azonnali zsákutca – nem érdemes tovább keresni.
            }
        }

        val candidates = bestCandidates ?: emptyList()
        if (candidates.isEmpty()) return SearchOutcome.EXHAUSTED

        val date = remaining[bestIndex]
        // A kiválasztott napot kivesszük a maradékból (indexes csere az utolsóval a gyors törléshez).
        val lastIdx = remaining.lastIndex
        remaining[bestIndex] = remaining[lastIdx]
        remaining.removeAt(lastIdx)

        val ranked = candidates
            .shuffled()
            .sortedBy { worker -> penaltyScore(worker, date, assignment) }

        for (candidate in ranked) {
            assignment[date] = candidate.id
            counts[candidate.id] = (counts[candidate.id] ?: 0) + 1

            val result = backtrack(
                remaining, assignment, counts, workers, allDays,
                previousMonthLastWorkerId, allowedOvertime, steps
            )
            if (result == SearchOutcome.FOUND) return SearchOutcome.FOUND
            if (result == SearchOutcome.STEP_LIMIT) {
                // A lépéskorlát felfelé propagál és a keresés leáll; a maradék lista pontos
                // helyreállítása felesleges, mert azt már senki sem olvassa.
                return SearchOutcome.STEP_LIMIT
            }

            // Visszalépés.
            counts[candidate.id] = (counts[candidate.id] ?: 1) - 1
            assignment.remove(date)
        }

        // A napot visszatesszük a maradékba (az eredeti indexre, hogy a hívó állapota konzisztens maradjon).
        remaining.add(date)
        return SearchOutcome.EXHAUSTED
    }

    /**
     * A KEMÉNY SZABÁLYOK alapján az adott napra beosztható dolgozók.
     * Mivel a napok nem sorrendben kerülnek kiosztásra, az előző ÉS a következő nap
     * ügyeletesét is nézni kell (pihenőidő), a hónapforduló kezelésével.
     */
    private fun eligibleWorkers(
        date: LocalDate,
        workers: List<Worker>,
        assignment: Map<LocalDate, String>,
        counts: Map<String, Int>,
        firstDay: LocalDate,
        previousMonthLastWorkerId: String?,
        allowedOvertime: Int
    ): List<Worker> {
        val previousDay = date.minus(DatePeriod(days = 1))
        val nextDay = date.plus(DatePeriod(days = 1))

        val previousWorkerId = assignment[previousDay]
            ?: if (date == firstDay) previousMonthLastWorkerId else null
        val nextWorkerId = assignment[nextDay]

        return workers.filter { worker ->
            val isAvailable = date !in worker.unavailableDates
            val withinCap = (counts[worker.id] ?: 0) < worker.maxShifts + allowedOvertime
            val restOk = worker.id != previousWorkerId && worker.id != nextWorkerId
            isAvailable && withinCap && restOk
        }
    }

    /**
     * Pontszámítás a jelöltek rendezéséhez: minél kisebb, annál alkalmasabb.
     */
    private fun penaltyScore(
        worker: Worker,
        date: LocalDate,
        assignment: Map<LocalDate, String>
    ): Int {
        var score = 0

        // 1. Teher-egyensúly: eddigi összes műszak.
        val totalShiftsSoFar = assignment.count { it.value == worker.id }
        score += totalShiftsSoFar * 10

        // 2. Preferált nap: erős kedvezmény.
        if (date in worker.preferredDates) score -= 25

        // 3. Hétvégi igazságosság.
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            val weekendShiftsSoFar = assignment.count { (d, id) ->
                id == worker.id && (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY)
            }
            score += weekendShiftsSoFar * 15
        }

        return score
    }

    /** Segédfüggvény: a megadott hónap összes napja [LocalDate] listaként. */
    private fun getDaysInMonth(year: Int, month: Int): List<LocalDate> {
        val nextYear = if (month == 12) year + 1 else year
        val nextMonth = if (month == 12) 1 else month + 1

        val firstOfNextMonth = LocalDate(nextYear, nextMonth, 1)
        val lastDayOfMonth = firstOfNextMonth.minus(DatePeriod(days = 1))

        return (1..lastDayOfMonth.dayOfMonth).map { day -> LocalDate(year, month, day) }
    }
}
