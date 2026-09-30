package com.myfit.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.delay

data class ToastMsg(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null, val id: Long = System.nanoTime())

@Stable
class Toaster {
    var current by mutableStateOf<ToastMsg?>(null)
        private set
    fun show(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        current = ToastMsg(text, actionLabel, action)
    }
    fun dismiss() { current = null }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

/** Glass toast at the top of the screen — used for "Saved · Undo" confirmations. */
@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val msg = toaster.current
    LaunchedEffect(msg?.id) { if (msg != null) { delay(3500); if (toaster.current?.id == msg.id) toaster.dismiss() } }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        AnimatedVisibility(
            msg != null,
            enter = slideInVertically(spring(0.7f, 400f)) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val m = msg ?: return@AnimatedVisibility
            Glass(Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(27.dp), tint = th.bgBottom.copy(alpha = 0.6f)) {
                Row(Modifier.padding(horizontal = 20.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.text, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    if (m.actionLabel != null) {
                        Text(
                            m.actionLabel, style = FitType.section, color = th.accentBright,
                            modifier = Modifier.clickableNoRipple { m.action?.invoke(); toaster.dismiss() }.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
