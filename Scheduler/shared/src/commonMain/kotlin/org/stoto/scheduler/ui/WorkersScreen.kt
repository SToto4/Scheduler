package org.stoto.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.stoto.scheduler.model.Worker
import org.stoto.scheduler.state.SchedulerViewModel

/**
 * Dolgozók nézet: felvétel/szerkesztés/törlés, havi max műszak, és a kieső/preferált
 * napok kijelölése egy naptáron a kiválasztott dolgozóhoz.
 */
@Composable
fun WorkersScreen(viewModel: SchedulerViewModel, modifier: Modifier = Modifier) {
    val workers by viewModel.workers
    val year by viewModel.year
    val month by viewModel.month

    var editorWorker by remember { mutableStateOf<Worker?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var selectedWorkerId by remember { mutableStateOf<String?>(null) }

    val selectedWorker = workers.firstOrNull { it.id == selectedWorkerId }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dolgozók", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = {
                editorWorker = null
                showEditor = true
            }) {
                Text("+ Új dolgozó")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (workers.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Még nincs dolgozó. Vegyél fel egyet az „Új dolgozó” gombbal.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weightHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workers, key = { it.id }) { worker ->
                    WorkerRow(
                        worker = worker,
                        selected = worker.id == selectedWorkerId,
                        onSelect = { selectedWorkerId = worker.id },
                        onEdit = {
                            editorWorker = worker
                            showEditor = true
                        },
                        onDelete = {
                            viewModel.removeWorker(worker.id)
                            if (selectedWorkerId == worker.id) selectedWorkerId = null
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectedWorker != null) {
            PreferenceCalendarCard(
                worker = selectedWorker,
                year = year,
                month = month,
                onToggleUnavailable = { viewModel.toggleUnavailableDate(selectedWorker.id, it) },
                onTogglePreferred = { viewModel.togglePreferredDate(selectedWorker.id, it) }
            )
        } else if (workers.isNotEmpty()) {
            Text(
                "Válassz egy dolgozót a napjai beállításához.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showEditor) {
        WorkerEditorDialog(
            existing = editorWorker,
            isNameTaken = { name -> viewModel.isNameTaken(name, ignoreWorkerId = editorWorker?.id) },
            onDismiss = { showEditor = false },
            onConfirm = { worker ->
                if (editorWorker == null) viewModel.addWorker(worker) else viewModel.updateWorker(worker)
                showEditor = false
            }
        )
    }
}

/** A LazyColumn ne tolja le a naptárat: fél magasságot adunk neki. */
private fun Modifier.weightHeight(): Modifier = this.then(Modifier.height(240.dp))

@Composable
private fun WorkerRow(
    worker: Worker,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(worker.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "Max műszak: ${worker.maxShifts}  •  kieső: ${worker.unavailableDates.size}  •  preferált: ${worker.preferredDates.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                AssistChip(onClick = onSelect, label = { Text("kiválasztva") })
                Spacer(Modifier.width(4.dp))
            }
            TextButton(onClick = onEdit) { Text("Szerkeszt") }
            TextButton(onClick = onDelete) { Text("Töröl") }
        }
    }
}

@Composable
private fun PreferenceCalendarCard(
    worker: Worker,
    year: Int,
    month: Int,
    onToggleUnavailable: (kotlinx.datetime.LocalDate) -> Unit,
    onTogglePreferred: (kotlinx.datetime.LocalDate) -> Unit
) {
    var mode by remember { mutableStateOf(PrefMode.UNAVAILABLE) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${worker.name} – ${DateUtil.monthName(month)} $year",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == PrefMode.UNAVAILABLE,
                    onClick = { mode = PrefMode.UNAVAILABLE },
                    label = { Text("Kieső nap") }
                )
                FilterChip(
                    selected = mode == PrefMode.PREFERRED,
                    onClick = { mode = PrefMode.PREFERRED },
                    label = { Text("Preferált nap") }
                )
            }
            Spacer(Modifier.height(8.dp))
            MonthCalendar(
                year = year,
                month = month,
                highlightFor = { date ->
                    when {
                        date in worker.unavailableDates -> DayHighlight.UNAVAILABLE
                        date in worker.preferredDates -> DayHighlight.PREFERRED
                        else -> DayHighlight.NONE
                    }
                },
                onDayClick = { date ->
                    when (mode) {
                        PrefMode.UNAVAILABLE -> onToggleUnavailable(date)
                        PrefMode.PREFERRED -> onTogglePreferred(date)
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Kattints egy napra a kijelöléshez. (Piros = kieső, kék = preferált.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class PrefMode { UNAVAILABLE, PREFERRED }

@Composable
private fun WorkerEditorDialog(
    existing: Worker?,
    isNameTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Worker) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var maxShiftsText by remember { mutableStateOf((existing?.maxShifts ?: 5).toString()) }

    val maxShifts = maxShiftsText.toIntOrNull()
    val nameBlank = name.isBlank()
    val duplicateName = !nameBlank && isNameTaken(name)
    val valid = !nameBlank && !duplicateName && maxShifts != null && maxShifts > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Új dolgozó" else "Dolgozó szerkesztése") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Név") },
                    singleLine = true,
                    isError = duplicateName,
                    modifier = Modifier.fillMaxWidth()
                )
                if (duplicateName) {
                    Text(
                        "Már létezik ilyen nevű dolgozó!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxShiftsText,
                    onValueChange = { maxShiftsText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Havi max műszak") },
                    singleLine = true,
                    isError = maxShifts == null || maxShifts <= 0,
                    modifier = Modifier.width(200.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val worker = (existing ?: Worker(
                        id = generateWorkerId(),
                        name = name
                    )).copy(
                        name = name.trim(),
                        maxShifts = maxShifts ?: 5
                    )
                    onConfirm(worker)
                }
            ) { Text("Mentés") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Mégse") }
        }
    )
}

/** Egyszerű, ütközésmentes azonosító új dolgozóhoz (idő-alapú). */
private fun generateWorkerId(): String = "w_" + kotlin.random.Random.nextLong(0, Long.MAX_VALUE).toString(36)
