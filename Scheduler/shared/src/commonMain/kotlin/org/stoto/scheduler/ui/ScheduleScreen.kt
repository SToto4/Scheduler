package org.stoto.scheduler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.stoto.scheduler.model.Shift
import org.stoto.scheduler.state.SchedulerViewModel

/** A havi beosztás megjelenítési módjai. */
private enum class ScheduleViewMode { CALENDAR, LIST }

/**
 * Havi beosztás nézet: év/hónap választó, generálás, napi táblázat (hétvége kiemelve,
 * ütközések jelölve), kattintásra dolgozócsere, statisztika és export/mentés gombok.
 */
@Composable
fun ScheduleScreen(viewModel: SchedulerViewModel, modifier: Modifier = Modifier) {
    val year by viewModel.year
    val month by viewModel.month
    val workers by viewModel.workers
    val schedule by viewModel.schedule
    val isGenerating by viewModel.isGenerating
    val violations by viewModel.violations

    var reassignTarget by remember { mutableStateOf<LocalDate?>(null) }
    var viewMode by remember { mutableStateOf(ScheduleViewMode.CALENDAR) }

    val violationDates = remember(violations) { violations.mapNotNull { it.date }.toSet() }
    val nameById = remember(workers) { workers.associate { it.id to it.name } }
    val preferredByWorker = remember(workers) { workers.associate { it.id to it.preferredDates } }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        MonthSelector(
            year = year,
            month = month,
            onYearChange = viewModel::setYear,
            onMonthChange = viewModel::setMonth
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.generate() },
                enabled = !isGenerating && workers.isNotEmpty()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Beosztás generálása")
            }
            if (schedule != null) {
                OutlinedButton(onClick = {
                    val path = viewModel.chooseSavePath(viewModel.defaultCsvName())
                    if (path != null) viewModel.exportCsv(path)
                }) { Text("Export CSV") }
                OutlinedButton(onClick = {
                    val path = viewModel.chooseSavePath(viewModel.defaultScheduleJsonName())
                    if (path != null) viewModel.saveSchedule(path)
                }) { Text("Mentés (JSON)") }
            }
        }

        if (workers.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Előbb vegyél fel dolgozókat a Dolgozók fülön.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (violations.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ViolationsCard(violations.map { it.message })
        }

        Spacer(Modifier.height(12.dp))

        val currentSchedule = schedule
        if (currentSchedule == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Még nincs beosztás. Nyomd meg a „Beosztás generálása” gombot.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Nézetváltó: Naptár / Lista
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nézet:", style = MaterialTheme.typography.titleSmall)
                FilterChip(
                    selected = viewMode == ScheduleViewMode.CALENDAR,
                    onClick = { viewMode = ScheduleViewMode.CALENDAR },
                    label = { Text("Naptár") }
                )
                FilterChip(
                    selected = viewMode == ScheduleViewMode.LIST,
                    onClick = { viewMode = ScheduleViewMode.LIST },
                    label = { Text("Lista") }
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(2f)) {
                    when (viewMode) {
                        ScheduleViewMode.CALENDAR -> ScheduleCalendar(
                            year = year,
                            month = month,
                            workerIdByDate = { date ->
                                currentSchedule.shifts.firstOrNull { it.date == date }?.assignedWorkerId
                            },
                            nameById = nameById,
                            preferredByWorker = preferredByWorker,
                            violationDates = violationDates,
                            onDayClick = { reassignTarget = it }
                        )
                        ScheduleViewMode.LIST -> {
                            Text("Napi beosztás", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(380.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    currentSchedule.shifts.sortedBy { it.date },
                                    key = { it.date.toString() }
                                ) { shift ->
                                    ShiftRow(
                                        shift = shift,
                                        workerName = shift.assignedWorkerId?.let { nameById[it] ?: it },
                                        hasViolation = shift.date in violationDates,
                                        onClick = { reassignTarget = shift.date }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    StatisticsCard(viewModel)
                }
            }
        }
    }

    reassignTarget?.let { date ->
        ReassignDialog(
            date = date,
            workers = workers,
            currentWorkerId = schedule?.shifts?.firstOrNull { it.date == date }?.assignedWorkerId,
            onDismiss = { reassignTarget = null },
            onSelect = { workerId ->
                viewModel.reassign(date, workerId)
                reassignTarget = null
            }
        )
    }
}

@Composable
private fun MonthSelector(
    year: Int,
    month: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hónap:", style = MaterialTheme.typography.titleMedium)
            MonthDropdown(month, onMonthChange)
            Spacer(Modifier.width(8.dp))
            Text("Év:", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onYearChange(year - 1) }) { Text("−") }
            Text("$year", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { onYearChange(year + 1) }) { Text("+") }
        }
    }
}

@Composable
private fun MonthDropdown(month: Int, onMonthChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(DateUtil.monthName(month))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DateUtil.monthNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onMonthChange(index + 1)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ShiftRow(
    shift: Shift,
    workerName: String?,
    hasViolation: Boolean,
    onClick: () -> Unit
) {
    val weekend = DateUtil.isWeekend(shift.date)
    val container = when {
        hasViolation -> MaterialTheme.colorScheme.errorContainer
        weekend -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(140.dp)) {
            Text(DateUtil.isoDate(shift.date), fontWeight = FontWeight.Medium)
            Text(
                DateUtil.dayName(shift.date) + if (weekend) " (hétvége)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = workerName ?: "— nincs beosztva —",
            modifier = Modifier.weight(1f),
            fontWeight = if (workerName == null) FontWeight.Normal else FontWeight.SemiBold,
            color = if (workerName == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (hasViolation) {
            Text("⚠", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatisticsCard(viewModel: SchedulerViewModel) {
    val loads = viewModel.statistics()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Összesítő", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (loads.isEmpty()) {
                Text("Nincs adat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                loads.forEach { load ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            load.workerName,
                            color = if (load.isOverloaded) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${load.totalShifts}/${load.maxShifts}  (hv: ${load.weekendShifts})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViolationsCard(messages: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Figyelmeztetések (${messages.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            messages.take(6).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (messages.size > 6) {
                Text("… és még ${messages.size - 6}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ReassignDialog(
    date: LocalDate,
    workers: List<org.stoto.scheduler.model.Worker>,
    currentWorkerId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ügyeletes – ${DateUtil.isoDate(date)}") },
        text = {
            Column {
                OptionRow(
                    label = "— nincs beosztva —",
                    selected = currentWorkerId == null,
                    onClick = { onSelect(null) }
                )
                workers.forEach { worker ->
                    OptionRow(
                        label = worker.name,
                        selected = worker.id == currentWorkerId,
                        onClick = { onSelect(worker.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Bezár") }
        }
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * Havi naptárrács a beosztáshoz (hétfőtől vasárnapig, vezető üres cellákkal).
 * Minden cellában a nap száma, a beosztott dolgozó neve (vagy „Üres” pirossal),
 * és vizuális jelölés: hétvége, szabályütközés (⚠), teljesült preferált nap (★).
 * Kattintásra a dolgozócsere dialógus nyílik meg.
 */
@Composable
private fun ScheduleCalendar(
    year: Int,
    month: Int,
    workerIdByDate: (LocalDate) -> String?,
    nameById: Map<String, String>,
    preferredByWorker: Map<String, Set<LocalDate>>,
    violationDates: Set<LocalDate>,
    onDayClick: (LocalDate) -> Unit
) {
    val days = DateUtil.daysOfMonth(year, month)
    val leading = DateUtil.leadingBlanks(year, month)
    val cells: List<LocalDate?> = List(leading) { null } + days
    val rows = cells.chunked(7)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth()) {
            DateUtil.shortDayNames.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (date != null) {
                            val workerId = workerIdByDate(date)
                            ScheduleDayCell(
                                date = date,
                                workerName = workerId?.let { nameById[it] ?: it },
                                hasViolation = date in violationDates,
                                isPreferredMet = workerId != null &&
                                    preferredByWorker[workerId]?.contains(date) == true,
                                onClick = { onDayClick(date) }
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun ScheduleDayCell(
    date: LocalDate,
    workerName: String?,
    hasViolation: Boolean,
    isPreferredMet: Boolean,
    onClick: () -> Unit
) {
    val weekend = DateUtil.isWeekend(date)
    val container = when {
        hasViolation -> MaterialTheme.colorScheme.errorContainer
        weekend -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        hasViolation -> MaterialTheme.colorScheme.onErrorContainer
        weekend -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        // Felső sor: nap száma + jelvények.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (weekend) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
            Row {
                if (isPreferredMet) Text("★", style = MaterialTheme.typography.labelMedium, color = contentColor)
                if (hasViolation) Text("⚠", style = MaterialTheme.typography.labelMedium, color = contentColor)
            }
        }
        // Alul: dolgozó neve / "Üres".
        Text(
            text = workerName ?: "Üres",
            modifier = Modifier.align(Alignment.BottomStart),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (workerName == null) FontWeight.Normal else FontWeight.SemiBold,
            color = if (workerName == null) MaterialTheme.colorScheme.error else contentColor
        )
    }
}
