package org.stoto.scheduler.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

/** Egy naptárcella megjelenési állapota. */
enum class DayHighlight { NONE, UNAVAILABLE, PREFERRED }

/**
 * Hétfővel kezdődő havi naptárrács. Minden napra megadható egy kiemelés [highlightFor],
 * és a napokra kattintás [onDayClick] eseményt vált ki.
 */
@Composable
fun MonthCalendar(
    year: Int,
    month: Int,
    modifier: Modifier = Modifier,
    highlightFor: (LocalDate) -> DayHighlight = { DayHighlight.NONE },
    onDayClick: (LocalDate) -> Unit = {}
) {
    val days = DateUtil.daysOfMonth(year, month)
    val leading = DateUtil.leadingBlanks(year, month)
    // Cellák: vezető üresek + napok, 7-es sorokra bontva.
    val cells: List<LocalDate?> = List(leading) { null } + days
    val rows = cells.chunked(7)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Napnév-fejléc
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
            Row(
                Modifier.fillMaxWidth().heightIn(min = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).heightIn(min = 36.dp)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                highlight = highlightFor(date),
                                onClick = { onDayClick(date) }
                            )
                        }
                    }
                }
                // A hiányzó cellák kitöltése, hogy az utolsó sor is igazodjon.
                repeat(7 - week.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, highlight: DayHighlight, onClick: () -> Unit) {
    val weekend = DateUtil.isWeekend(date)
    val background = when (highlight) {
        DayHighlight.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
        DayHighlight.PREFERRED -> MaterialTheme.colorScheme.primaryContainer
        DayHighlight.NONE -> if (weekend) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        }
    }
    val contentColor = when (highlight) {
        DayHighlight.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
        DayHighlight.PREFERRED -> MaterialTheme.colorScheme.onPrimaryContainer
        DayHighlight.NONE -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (weekend) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
