package org.stoto.scheduler.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.datetime.LocalDate
import org.stoto.scheduler.domain.ScheduleEditor
import org.stoto.scheduler.domain.ScheduleStatistics
import org.stoto.scheduler.domain.ShiftScheduler
import org.stoto.scheduler.model.Schedule
import org.stoto.scheduler.model.Worker
import org.stoto.scheduler.storage.SchedulerRepository

/**
 * Központi állapotkezelő (state holder), amely összeköti a domain és storage réteget a UI-val.
 *
 * Compose [State]-alapú (nincs extra coroutines-függőség): a felület a kiolvasott
 * állapotokra (pl. [workers], [schedule], [statusMessage]) rekomponálódik.
 *
 * Felelősségek: dolgozók CRUD-ja, hónapválasztás, generálás indítása, manuális
 * szerkesztés (átosztás/csere) szabályellenőrzéssel, valamint mentés/betöltés/export.
 */
class SchedulerViewModel(
    private val scheduler: ShiftScheduler = ShiftScheduler(),
    private val editor: ScheduleEditor = ScheduleEditor(),
    private val repository: SchedulerRepository = SchedulerRepository()
) {

    // --- Visszaolvasható állapot (a UI ezekre figyel) ---

    private val _workers = mutableStateOf<List<Worker>>(emptyList())
    val workers: State<List<Worker>> = _workers

    private val _schedule = mutableStateOf<Schedule?>(null)
    val schedule: State<Schedule?> = _schedule

    private val _year = mutableStateOf(DEFAULT_YEAR)
    val year: State<Int> = _year

    private val _month = mutableStateOf(DEFAULT_MONTH)
    val month: State<Int> = _month

    /** A legutóbbi művelet rövid, felhasználónak szánt üzenete (siker vagy információ). */
    private val _statusMessage = mutableStateOf<String?>(null)
    val statusMessage: State<String?> = _statusMessage

    /** A legutóbbi hiba üzenete (pl. sikertelen generálás, I/O hiba). */
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    /** A jelenlegi beosztás kemény-szabály sértései (figyelmeztetésként jelenik meg). */
    private val _violations = mutableStateOf<List<ScheduleEditor.Violation>>(emptyList())
    val violations: State<List<ScheduleEditor.Violation>> = _violations

    /** Igaz, amíg generálás fut (a UI letilthatja a gombot / spinnert mutathat). */
    private val _isGenerating = mutableStateOf(false)
    val isGenerating: State<Boolean> = _isGenerating

    // --- Hónapválasztás ---

    fun setYear(year: Int) {
        if (year in 1..9999) _year.value = year
    }

    fun setMonth(month: Int) {
        if (month in 1..12) _month.value = month
    }

    // --- Dolgozók CRUD ---

    fun addWorker(worker: Worker) {
        if (_workers.value.any { it.id == worker.id }) {
            _errorMessage.value = "Már van dolgozó ezzel az azonosítóval: ${worker.id}"
            return
        }
        _workers.value = _workers.value + worker
        clearMessages()
    }

    fun updateWorker(worker: Worker) {
        val index = _workers.value.indexOfFirst { it.id == worker.id }
        if (index < 0) {
            _errorMessage.value = "Nem található a frissítendő dolgozó: ${worker.id}"
            return
        }
        _workers.value = _workers.value.toMutableList().also { it[index] = worker }
        clearMessages()
    }

    fun removeWorker(workerId: String) {
        _workers.value = _workers.value.filterNot { it.id == workerId }
        // Ha a dolgozó szerepelt a beosztásban, ürítsük a napjait és frissítsük a figyelmeztetéseket.
        _schedule.value?.let { current ->
            val cleaned = current.copy(
                shifts = current.shifts.map { shift ->
                    if (shift.assignedWorkerId == workerId) shift.copy(assignedWorkerId = null) else shift
                }
            )
            _schedule.value = cleaned
            recomputeViolations()
        }
        clearMessages()
    }

    /** Kényelmi művelet: egy nap be-/kikapcsolása a dolgozó kieső napjai között. */
    fun toggleUnavailableDate(workerId: String, date: LocalDate) {
        updateDates(workerId) { worker ->
            val set = worker.unavailableDates.toMutableSet()
            if (!set.add(date)) set.remove(date)
            worker.copy(
                unavailableDates = set,
                // A kieső és preferált nap kizárja egymást.
                preferredDates = worker.preferredDates - date
            )
        }
    }

    /** Kényelmi művelet: egy nap be-/kikapcsolása a dolgozó preferált napjai között. */
    fun togglePreferredDate(workerId: String, date: LocalDate) {
        updateDates(workerId) { worker ->
            val set = worker.preferredDates.toMutableSet()
            if (!set.add(date)) set.remove(date)
            worker.copy(
                preferredDates = set,
                unavailableDates = worker.unavailableDates - date
            )
        }
    }

    private inline fun updateDates(workerId: String, transform: (Worker) -> Worker) {
        val index = _workers.value.indexOfFirst { it.id == workerId }
        if (index < 0) return
        _workers.value = _workers.value.toMutableList().also {
            it[index] = transform(it[index])
        }
    }

    // --- Generálás ---

    fun generate(previousMonthLastWorkerId: String? = null) {
        if (_workers.value.isEmpty()) {
            _errorMessage.value = "Adj hozzá legalább egy dolgozót a generálás előtt."
            return
        }
        _isGenerating.value = true
        clearMessages()
        try {
            val result = scheduler.generate(
                year = _year.value,
                month = _month.value,
                workers = _workers.value,
                previousMonthLastWorkerId = previousMonthLastWorkerId
            )
            when (result) {
                is ShiftScheduler.GenerationResult.Success -> {
                    _schedule.value = result.schedule
                    recomputeViolations()
                    _statusMessage.value = if (result.appliedOvertime > 0) {
                        "Beosztás elkészült ${result.appliedOvertime} túlóra engedélyezésével."
                    } else {
                        "Beosztás sikeresen elkészült."
                    }
                }
                is ShiftScheduler.GenerationResult.Failure -> {
                    _errorMessage.value = result.reason
                }
            }
        } finally {
            _isGenerating.value = false
        }
    }

    // --- Manuális szerkesztés (szabályellenőrzéssel) ---

    /**
     * Egy nap ügyeletesének módosítása. A módosítás mindig alkalmazódik, de a visszaadott
     * lista tartalmazza az általa okozott kemény-szabály sértéseket (a UI figyelmeztethet).
     */
    fun reassign(date: LocalDate, newWorkerId: String?): List<ScheduleEditor.Violation> {
        val current = _schedule.value ?: return emptyList()
        _schedule.value = editor.reassignShift(current, date, newWorkerId)
        recomputeViolations()
        return _violations.value.filter { it.date == date || it.workerId == newWorkerId }
    }

    /** Két nap ügyeletesének cseréje, a keletkező szabálysértések visszaadásával. */
    fun swap(dateA: LocalDate, dateB: LocalDate): List<ScheduleEditor.Violation> {
        val current = _schedule.value ?: return emptyList()
        _schedule.value = editor.swapShifts(current, dateA, dateB)
        recomputeViolations()
        return _violations.value.filter { it.date == dateA || it.date == dateB }
    }

    /** Előzetes ellenőrzés tényleges módosítás nélkül (pl. amíg a felhasználó választ). */
    fun previewReassignment(date: LocalDate, newWorkerId: String?): List<ScheduleEditor.Violation> {
        val current = _schedule.value ?: return emptyList()
        return editor.validateReassignment(current, _workers.value, date, newWorkerId)
    }

    // --- Statisztika ---

    fun statistics(): List<ScheduleStatistics.WorkerLoad> {
        val current = _schedule.value ?: return emptyList()
        return ScheduleStatistics.workerLoads(current, _workers.value)
    }

    // --- Perzisztencia és export ---

    fun saveWorkers(path: String) = runCatching {
        repository.saveWorkers(_workers.value, path)
    }.onSuccess { _statusMessage.value = "Dolgozók mentve: $path" }
        .onFailure { _errorMessage.value = "Mentési hiba: ${it.message}" }
        .let {}

    fun loadWorkers(path: String) = runCatching {
        repository.loadWorkers(path)
    }.onSuccess {
        _workers.value = it
        _statusMessage.value = "Dolgozók betöltve (${it.size} db)."
    }.onFailure { _errorMessage.value = "Betöltési hiba: ${it.message}" }
        .let {}

    fun saveSchedule(path: String) = withSchedule { schedule ->
        repository.saveSchedule(schedule, path)
        _statusMessage.value = "Beosztás mentve: $path"
    }

    fun loadSchedule(path: String) = runCatching {
        repository.loadSchedule(path)
    }.onSuccess {
        _schedule.value = it
        _year.value = it.year
        _month.value = it.month
        recomputeViolations()
        _statusMessage.value = "Beosztás betöltve."
    }.onFailure { _errorMessage.value = "Betöltési hiba: ${it.message}" }
        .let {}

    fun exportCsv(path: String, delimiter: Char = ';') = withSchedule { schedule ->
        repository.exportScheduleCsv(schedule, _workers.value, path, delimiter)
        _statusMessage.value = "CSV exportálva: $path"
    }

    /** Natív mentés-dialógus (ahol támogatott); `null` ha nincs / megszakítva. */
    fun chooseSavePath(suggestedName: String): String? = repository.chooseSavePath(suggestedName)

    /** Natív megnyitás-dialógus (ahol támogatott); `null` ha nincs / megszakítva. */
    fun chooseOpenPath(): String? = repository.chooseOpenPath()

    /** Javasolt fájlnevek az aktuális hónaphoz. */
    fun defaultCsvName(): String = SchedulerRepository.defaultCsvName(_year.value, _month.value)
    fun defaultScheduleJsonName(): String =
        SchedulerRepository.defaultScheduleJsonName(_year.value, _month.value)
    fun defaultWorkersName(): String = SchedulerRepository.DEFAULT_WORKERS_JSON_NAME

    // --- Üzenetkezelés ---

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    // --- Segédek ---

    private fun recomputeViolations() {
        val current = _schedule.value
        _violations.value = if (current == null) {
            emptyList()
        } else {
            editor.validateSchedule(current, _workers.value)
        }
    }

    private inline fun withSchedule(action: (Schedule) -> Unit) {
        val current = _schedule.value
        if (current == null) {
            _errorMessage.value = "Nincs mentendő/exportálható beosztás."
            return
        }
        runCatching { action(current) }
            .onFailure { _errorMessage.value = "I/O hiba: ${it.message}" }
    }

    companion object {
        const val DEFAULT_YEAR: Int = 2026
        const val DEFAULT_MONTH: Int = 1
    }
}
