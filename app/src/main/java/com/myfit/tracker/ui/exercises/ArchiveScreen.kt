package com.myfit.tracker.ui.exercises

import com.myfit.tracker.ui.theme.Duo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme

/** Archived exercises and workout templates — hidden from lists, history intact, one tap to restore. */
@Composable
fun ArchiveScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val exercises by container.exerciseRepo.archived.collectAsState(initial = emptyList())
    val templates by container.workoutRepo.archivedTemplates.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Archive", { nav.pop() }, "Hidden from lists · all history kept")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (exercises.isEmpty() && templates.isEmpty()) item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBubble(Duo.Inventory2, th.accent, size = 52.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Nothing archived", style = FitType.section, color = th.text)
                        Caption("Archive an exercise from its detail page, or a template from its menu in Train.")
                    }
                }
            }
            if (templates.isNotEmpty()) {
                item { Text("TEMPLATES", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 6.dp)) }
                items(templates, key = { "t" + it.id }) { tp ->
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Duo.Inventory2, th.accent)
                            Spacer(Modifier.width(10.dp))
                            Text(tp.name, style = FitType.section, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            GlassButton("Restore", {
                                container.write { container.workoutRepo.unarchiveTemplate(tp.id) }; toaster.show("Template restored")
                            }, icon = Duo.Unarchive, height = 40.dp)
                        }
                    }
                }
            }
            if (exercises.isNotEmpty()) {
                item { Text("EXERCISES", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 6.dp)) }
                items(exercises, key = { "e" + it.id }) { ex ->
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = { nav.push(Overlay.ExerciseDetail(ex.id)) }) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExerciseImage(ex, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption(ex.primaryMuscle.replaceFirstChar { it.uppercase() })
                            }
                            GlassButton("Restore", {
                                container.write { container.exerciseRepo.unarchive(ex.id) }; toaster.show("${ex.name} restored")
                            }, icon = Duo.Unarchive, height = 40.dp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
