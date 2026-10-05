package com.myfit.tracker.ui.programs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.arena.CastImage
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSearchField
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GOAL_COLORS = mapOf(
    "muscle" to (Color(0xFFE0442A) to Color(0xFF7A1E3A)), "strength" to (Color(0xFF3D4A5C) to Color(0xFF12161D)),
    "fatloss" to (Color(0xFFFF8A3D) to Color(0xFFC2185B)), "tone" to (Color(0xFFE86FA6) to Color(0xFF7B3FA0)),
    "endurance" to (Color(0xFF1FA2C9) to Color(0xFF173D7A)), "athletic" to (Color(0xFF2DBE60) to Color(0xFF0F5A3D)),
    "mobility" to (Color(0xFF7FC8A9) to Color(0xFF2E6B7A)), "health" to (Color(0xFF4FB3A9) to Color(0xFF225E8C)),
)
private fun Program.colors() = GOAL_COLORS[goals.firstOrNull()] ?: (Color(0xFF555B66) to Color(0xFF22262D))

private object CoverCache {
    // byte-sized (~12 MB) — covers are 768×448, so ~9 full covers stay decoded
    private val cache = object : android.util.LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val missing = mutableSetOf<String>()
    suspend fun get(c: android.content.Context, id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        if (id in missing) return null
        return withContext(Dispatchers.IO) {
            runCatching { c.assets.open("programs/$id.webp").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        }?.also { cache.put(id, it) } ?: run { missing.add(id); null }
    }
}

/** Program cover: AI artwork when bundled, else a goal-coloured gradient — always with the coach character badge. */
@Composable
fun ProgramCover(p: Program, height: Dp, modifier: Modifier = Modifier, corner: Dp = 22.dp, showLevel: Boolean = true) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, p.id) { value = CoverCache.get(ctx, p.id) }
    val (c1, c2) = p.colors()
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(c1, c2)))) {
        val i = img
        if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Duo.FitnessCenter, null, tint = Color.White.copy(alpha = 0.13f), modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).size(height * 0.62f))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        if (showLevel) Text(label(LEVELS, p.level).uppercase(), style = FitType.overline, color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 5.dp))
        Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(height * 0.42f).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
            .border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            CastImage(p.coach, height * 0.38f)
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Text(text, style = FitType.label, color = if (selected) th.onAccent else th.text, maxLines = 1,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) th.accent else th.text.copy(alpha = 0.07f))
            .clickableNoRipple(onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable
private fun Tag(text: String) {
    val th = LocalFitTheme.current
    Text(text, style = FitType.caption, color = th.textDim, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(th.text.copy(alpha = 0.06f)).padding(horizontal = 9.dp, vertical = 4.dp))
}

/** Train → Programs: search, filters, the program being followed, and every program as a cover card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramsScreen(container: AppContainer, bottomPad: Int) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val ver by ProgramLib.version.collectAsState()
    val all = remember(ver) { ProgramLib.all(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ProgramFilter()) }
    var sheet by remember { mutableStateOf(false) }
    val follow by ProgramEngine.follow.collectAsState()
    val active = remember(follow, ver) { ProgramLib.byId(ctx, follow?.id) }
    val shown = remember(query, filter, all) { all.filter { filter.matches(it) && it.matchesQuery(query) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassSearchField(query, { query = it }, "Search programs", Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Box {
                        Glass(Modifier.size(50.dp), shape = CircleShape, onClick = { sheet = true }) {
                            Icon(Duo.Tune, "Filters", tint = th.text, modifier = Modifier.align(Alignment.Center))
                        }
                        if (filter.count > 0) Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(th.accent), contentAlignment = Alignment.Center) {
                            Text("${filter.count}", style = FitType.overline, color = th.onAccent)
                        }
                    }
                }
            }
            if (active != null && follow != null && query.isBlank() && filter.count == 0) item(key = "active") {
                ActiveProgramCard(container, active, follow!!) { nav.push(Overlay.ProgramDetail(active.id)) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(if (query.isBlank() && filter.count == 0) "All programs · ${all.size}" else "${shown.size} matching", Modifier.weight(1f))
                    if (filter.count > 0) Text("Clear filters", style = FitType.label, color = th.accentBright,
                        modifier = Modifier.clickableNoRipple { filter = ProgramFilter() }.padding(6.dp))
                }
            }
            if (shown.isEmpty()) item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("No programs match", style = FitType.section, color = th.text)
                        Caption("Try fewer filters or a different word.")
                    }
                }
            }
            items(shown, key = { it.id }) { p -> ProgramCard(p, p.id == follow?.id) { nav.push(Overlay.ProgramDetail(p.id)) } }
        }
        GlassSheet(sheet, { sheet = false }) { FilterSheet(filter, all, onApply = { filter = it; sheet = false }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramCard(p: Program, following: Boolean, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(10.dp)) {
            ProgramCover(p, 150.dp)
            Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (following) { Spacer(Modifier.width(8.dp)); Text("FOLLOWING", style = FitType.overline, color = th.onAccent,
                        modifier = Modifier.clip(CircleShape).background(th.accent).padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
                Caption(p.tag)
                Spacer(Modifier.height(8.dp))
                Text("${p.weeks} weeks · ${p.dpw} days/week · ~${p.mins} min", style = FitType.label, color = th.text)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.goals.forEach { Tag(label(GOALS, it)) }
                    Tag(label(EQUIP, p.equip))
                    if (p.gender != "all") Tag("For " + label(GENDERS, p.gender).lowercase())
                }
            }
        }
    }
}

@Composable
private fun ActiveProgramCard(container: AppContainer, p: Program, f: Follow, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val done by remember(f) { ProgramEngine.sessionsDone(container, f) }.collectAsState(0)
    val activeW by container.workoutRepo.inProgress.collectAsState(null)
    val pos = ProgramEngine.position(p, done)
    Glass(Modifier.fillMaxWidth(), onClick = onOpen) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.horizontalGradient(listOf(th.accent.copy(alpha = 0.45f), th.accent.copy(alpha = 0.06f)))) })
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) { CastImage(p.coach, 48.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("YOUR PROGRAM", style = FitType.overline, color = th.text)
                    Text(p.name, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Caption(if (pos.finished) "Completed — ${p.sessions} sessions. Amazing work!"
                    else "Week ${pos.week} of ${p.weeks} · ${p.phase(pos.week).label} · session ${done + 1} of ${p.sessions}", color = th.text)
                }
            }
            Spacer(Modifier.height(12.dp))
            com.myfit.tracker.ui.components.GlassProgressBar((done.toFloat() / p.sessions).coerceIn(0f, 1f), th.accentBright)
            if (!pos.finished) {
                Spacer(Modifier.height(12.dp))
                val day = p.days[pos.day]
                Text("Up next: ${day.name}", style = FitType.section, color = th.text)
                Caption(day.focus + " · ${day.items.size} exercises")
                Spacer(Modifier.height(10.dp))
                AccentButton("Start session", {
                    if (activeW != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(activeW!!.id)) }
                    else scope.launch { nav.push(Overlay.Gym(ProgramEngine.startSession(container, p, f, pos))) }
                }, icon = Duo.PlayArrow, height = 46.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(initial: ProgramFilter, all: List<Program>, onApply: (ProgramFilter) -> Unit) {
    val th = LocalFitTheme.current
    var f by remember(initial) { mutableStateOf(initial) }
    val n = remember(f) { all.count { f.matches(it) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Filters", style = FitType.title, color = th.text, modifier = Modifier.weight(1f))
        Text("Clear all", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { f = ProgramFilter() }.padding(6.dp))
    }
    @Composable fun <T> group(title: String, opts: List<Pair<T, String>>, sel: Set<T>, set: (Set<T>) -> Unit) {
        Spacer(Modifier.height(14.dp))
        SectionTitle(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { (k, l) -> Chip(l, k in sel) { set(if (k in sel) sel - k else sel + k) } }
        }
    }
    group("For", GENDERS, f.gender) { f = f.copy(gender = it) }
    group("Level", LEVELS, f.level) { f = f.copy(level = it) }
    group("Goal", GOALS, f.goal) { f = f.copy(goal = it) }
    group("Equipment", EQUIP, f.equip) { f = f.copy(equip = it) }
    group("Days per week", DAYS.map { it to if (it == 6) "6+" else "$it" }, f.days) { f = f.copy(days = it) }
    Spacer(Modifier.height(20.dp))
    AccentButton(if (n == 0) "No programs match" else "Show $n program${if (n == 1) "" else "s"}", { onApply(f) }, Modifier.fillMaxWidth(), height = 50.dp)
}
