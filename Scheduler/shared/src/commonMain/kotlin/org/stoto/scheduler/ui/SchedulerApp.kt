package org.stoto.scheduler.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.stoto.scheduler.state.SchedulerViewModel

private enum class Tab(val label: String) {
    WORKERS("Dolgozók"),
    SCHEDULE("Beosztás")
}

/**
 * Az alkalmazás gyökere: Material 3 Scaffold, alsó navigáció a két fő nézet között,
 * és Snackbar az állapot-/hibaüzenetekhez. A [SchedulerViewModel] itt jön létre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerApp(viewModel: SchedulerViewModel = remember { SchedulerViewModel() }) {
    var tab by remember { mutableStateOf(Tab.WORKERS) }
    val snackbarHostState = remember { SnackbarHostState() }

    val status by viewModel.statusMessage
    val error by viewModel.errorMessage

    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar("Hiba: $it")
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Ügyeletbeosztó") })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {},
                        label = { Text(entry.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.WORKERS -> WorkersScreen(viewModel)
                Tab.SCHEDULE -> ScheduleScreen(viewModel)
            }
        }
    }
}
