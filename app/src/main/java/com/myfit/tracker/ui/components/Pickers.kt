package com.myfit.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** Date + time chips. Defaults to "now"; tap either to back-date an entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRow(label: String, epochMs: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val zone = Clock.zone()
    val zdt = Instant.ofEpochMilli(epochMs).atZone(zone)
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.label, color = th.textDim, modifier = Modifier.weight(1f))
        Chip(Icons.Rounded.CalendarMonth, zdt.toLocalDate().format(dayFmt)) { showDate = true }
        Spacer(Modifier.width(8.dp))
        Chip(Icons.Rounded.Schedule, zdt.toLocalTime().format(timeFmt)) { showTime = true }
    }

    if (showDate) {
        // DatePicker works in UTC midnight millis
        val initialUtc = zdt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(d.atTime(zdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(zdt.hour, zdt.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton({
                    onChange(zdt.toLocalDate().atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli())
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

/** Minute-of-day picker (for profile wake/sleep/workout times). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuteOfDayChip(minOfDay: Int, onChange: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Chip(Icons.Rounded.Schedule, "%02d:%02d".format(Locale.US, minOfDay / 60, minOfDay % 60)) { show = true }
    if (show) {
        val state = rememberTimePickerState(minOfDay / 60, minOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton({ onChange(state.hour * 60 + state.minute); show = false }) { Text("OK") } },
            dismissButton = { TextButton({ show = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.height(38.dp), shape = RoundedCornerShape(19.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = th.textDim, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = FitType.label, color = th.text)
        }
    }
}

fun LocalDate.pretty(): String = format(dayFmt)
