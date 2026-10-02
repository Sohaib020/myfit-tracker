package com.myfit.tracker.ui.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)

/** Label + a chip that opens a date picker (date only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateOnlyRow(label: String, date: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    var show by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.label, color = th.textDim, modifier = Modifier.weight(1f))
        Glass(Modifier.height(38.dp), shape = RoundedCornerShape(19.dp), onClick = { show = true }) {
            Row(Modifier.padding(horizontal = 12.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Duo.CalendarMonth, null, tint = th.textDim, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(date?.format(longFmt) ?: "Pick a date", style = FitType.label, color = th.text)
            }
        }
    }
    if (show) {
        val initial = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms -> onChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ show = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
internal fun Bullet(text: String, color: Color? = null) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(color ?: th.accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = FitType.body, color = th.text)
    }
}

/** Number pad used by the PIN gate and the PIN setup sheet. */
@Composable
private fun PinPad(pin: String, onPin: (String) -> Unit, maxLen: Int = 6) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(20.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < pin.length) th.text else th.textFaint.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { k ->
                    if (k.isEmpty()) Spacer(Modifier.size(68.dp))
                    else Glass(Modifier.size(68.dp), shape = CircleShape, onClick = {
                        tick()
                        if (k == "<") onPin(pin.dropLast(1)) else if (pin.length < maxLen) onPin(pin + k)
                    }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (k == "<") Icon(Duo.KeyboardArrowLeft, "Delete", tint = th.text, modifier = Modifier.size(26.dp))
                            else Text(k, style = FitType.title, color = th.text)
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen lock shown before cycle data when the app lock is on. */
@Composable
internal fun CyclePinGate(onUnlocked: () -> Unit, onForgot: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var tries by remember { mutableStateOf(0) }
    var forgot by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBubble(Duo.Lock, th.success, 52.dp)
            Spacer(Modifier.height(14.dp))
            Text("Enter your PIN", style = FitType.title, color = th.text)
            Spacer(Modifier.height(4.dp))
            Caption(if (error) "That PIN didn't match. Try again." else "This section is locked for privacy.", color = if (error) th.danger else null)
            Spacer(Modifier.height(22.dp))
            PinPad(pin, { pin = it; error = false })
            Spacer(Modifier.height(16.dp))
            AccentButton("Unlock", {
                if (tries >= 10) return@AccentButton
                if (CyclePrefs.checkPin(ctx, pin)) { CycleLockSession.unlock(); onUnlocked() }
                else { error = true; pin = ""; tries++ }
            }, Modifier.fillMaxWidth(), icon = Duo.Lock, enabled = pin.length >= 4 && tries < 10, height = 50.dp)
            if (tries >= 10) { Spacer(Modifier.height(8.dp)); Caption("Too many tries. Close the app and try again later.", color = th.warning) }
            Spacer(Modifier.height(8.dp))
            GlassButton("Forgot PIN?", { forgot = true }, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
    GlassSheet(forgot, { forgot = false }) {
        Text("Forgot your PIN?", style = FitType.title, color = th.text)
        Spacer(Modifier.height(8.dp))
        Caption("For your privacy the PIN can't be recovered. You can reset it by deleting all cycle data on this phone (period logs, settings and anything MyFit shared to Health Connect). This can't be undone.")
        Spacer(Modifier.height(16.dp))
        GlassButton("Delete cycle data and reset", { forgot = false; onForgot() }, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline, height = 48.dp)
        Spacer(Modifier.height(8.dp))
        AccentButton("Keep trying", { forgot = false }, Modifier.fillMaxWidth(), height = 48.dp)
    }
}

/** Sheet to choose a new 4–6 digit PIN (entered twice). */
@Composable
internal fun PinSetupSheet(visible: Boolean, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var first by remember(visible) { mutableStateOf<String?>(null) }
    var pin by remember(visible) { mutableStateOf("") }
    GlassSheet(visible, onDismiss) {
        Text(if (first == null) "Choose a PIN" else "Enter it again", style = FitType.title, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Caption("4 to 6 digits. Asked when you open Cycle (after 5 minutes away). It's stored only as a scrambled code on this phone.", Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        PinPad(pin, { pin = it })
        Spacer(Modifier.height(16.dp))
        AccentButton(if (first == null) "Next" else "Turn on lock", {
            val f = first
            if (f == null) { first = pin; pin = "" }
            else if (f == pin) onSet(pin)
            else { toaster.show("PINs didn't match — start again"); first = null; pin = "" }
        }, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = pin.length >= 4, height = 50.dp)
    }
}
