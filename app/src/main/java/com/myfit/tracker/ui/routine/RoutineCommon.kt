package com.myfit.tracker.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.util.Locale

internal fun fmtMin(m: Int): String {
    val x = ((m % 1440) + 1440) % 1440
    return "%02d:%02d".format(Locale.US, x / 60, x % 60)
}

/** "5 h 12 m" / "12 m" / "0 m". */
internal fun fmtDur(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0)
    val h = totalMin / 60; val m = totalMin % 60
    return if (h > 0) "$h h ${m} m" else "$m m"
}

/** "05:12:09" — for the live fasting timer. */
internal fun fmtClock(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(Locale.US, s / 3600, (s % 3600) / 60, s % 60)
}

internal fun fmtNum(v: Double): String =
    if (v == Math.floor(v) && v < 1e7) v.toLong().toString() else "%.1f".format(Locale.US, v)

@Composable
internal fun RoutineTextField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Glass(modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.CenterStart)) {
            if (value.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint)
            BasicTextField(
                value, { onChange(it.take(120)) },
                singleLine = true,
                textStyle = FitType.body.copy(color = th.text),
                cursorBrush = SolidColor(th.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Day-of-week chips. Bit0 = Monday … bit6 = Sunday. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    val labels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val bit = 1 shl i
            GlassChip(l, (mask and bit) != 0, { onChange(mask xor bit) })
        }
    }
}

/** Row with a label (tap = onClick) and a switch. */
@Composable
internal fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, onClick: (() -> Unit)? = null) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier)) {
            Text(title, style = FitType.body, color = th.text)
            if (sub != null) Caption(sub)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = th.accent, checkedThumbColor = th.onAccent,
                uncheckedTrackColor = th.textFaint.copy(alpha = 0.3f), uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = FitType.label, color = LocalFitTheme.current.textDim, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}
