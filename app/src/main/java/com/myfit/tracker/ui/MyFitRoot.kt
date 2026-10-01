package com.myfit.tracker.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SportsGymnastics
import com.myfit.tracker.ui.exercises.ExerciseDetailScreen
import com.myfit.tracker.ui.exercises.ExerciseEditorScreen
import com.myfit.tracker.ui.exercises.ExercisesScreen
import com.myfit.tracker.ui.gym.FinishWorkoutScreen
import com.myfit.tracker.ui.gym.GymModeScreen
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Nav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.train.TemplateEditorScreen
import com.myfit.tracker.ui.train.TrainScreen
import com.myfit.tracker.ui.train.WorkoutDetailScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.UserProfile
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.ToastHost
import com.myfit.tracker.ui.components.Toaster
import com.myfit.tracker.ui.dashboard.DashboardScreen
import com.myfit.tracker.ui.dashboard.DashboardViewModel
import com.myfit.tracker.ui.entries.EntrySheetContent
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.nav.LiquidTabBar
import com.myfit.tracker.ui.nav.TabItem
import com.myfit.tracker.ui.onboarding.OnboardingScreen
import com.myfit.tracker.ui.settings.BackgroundImages
import com.myfit.tracker.ui.settings.MeScreen
import com.myfit.tracker.ui.theme.Backdrop
import com.myfit.tracker.ui.theme.LocalBackdrop
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import com.myfit.tracker.ui.theme.drawBackdrop
import com.myfit.tracker.ui.timeline.TimelineScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private sealed interface ProfileState {
    data object Loading : ProfileState
    data class Ready(val profile: UserProfile?) : ProfileState
}

@Composable
fun MyFitRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val profileState by remember { container.profileRepo.profile.map { ProfileState.Ready(it) } }
        .collectAsStateWithLifecycle(initialValue = ProfileState.Loading)

    val s = settings ?: return          // settings load in a few ms; draw nothing until then
    val theme = Themes.byId(s.themeId)
    val backdrop = remember { Backdrop() }
    backdrop.theme = theme

    // custom wallpaper
    LaunchedEffect(s.customBackground) {
        backdrop.image = s.customBackground?.let { name ->
            withContext(Dispatchers.IO) { BackgroundImages.load(container.filesDir, name)?.asImageBitmap() }
        }
    }
    // ambient animation clock
    // Motion: 0 smooth (every frame), 1 balanced (~30 fps, default), 2 battery saver (still).
    // Only changing `time` triggers a redraw, so skipping updates really skips GPU work.
    val animate = s.animatedBackground && s.motion != 2
    LaunchedEffect(animate, s.motion) {
        if (!animate) return@LaunchedEffect
        val minStep = if (s.motion == 0) 0L else 32L
        var last = -1L
        var acc = 0L
        while (true) withFrameMillis { now ->
            if (last >= 0) acc += now - last
            last = now
            if (acc >= minStep) { backdrop.time.floatValue += acc.coerceAtMost(100L) / 1000f; acc = 0L }
        }
    }
    // status-bar icon colour follows the theme
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = theme.isLight
            WindowCompat.getInsetsController(w, view).isAppearanceLightNavigationBars = theme.isLight
        }
    }

    val toaster = remember { Toaster() }
    MyFitTheme(theme, s) {
        CompositionLocalProvider(LocalBackdrop provides backdrop, LocalToaster provides toaster) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { backdrop.rootSize = Size(it.width.toFloat(), it.height.toFloat()) }
            ) {
                // the backdrop is recorded once per frame into a shared layer that every glass surface replays
                val bgLayer = rememberGraphicsLayer()
                val blurLayer = rememberGraphicsLayer()
                backdrop.layer = bgLayer
                backdrop.blurLayer = blurLayer
                val blurPx = with(androidx.compose.ui.platform.LocalDensity.current) { (26.dp * s.blurAmount).toPx() }
                Canvas(Modifier.fillMaxSize()) {
                    val t = backdrop.time.floatValue
                    bgLayer.record { drawBackdrop(backdrop.theme, backdrop.image, t, size.width, size.height) }
                    drawLayer(bgLayer)
                    // one blur per frame, shared by every glass card (Android 12+)
                    if (android.os.Build.VERSION.SDK_INT >= 31 && !com.myfit.tracker.CrashGuard.safeMode) {
                        blurLayer.renderEffect = if (blurPx > 0.5f) androidx.compose.ui.graphics.BlurEffect(blurPx, blurPx, androidx.compose.ui.graphics.TileMode.Clamp) else null
                        blurLayer.record { drawLayer(bgLayer) }
                    }
                }
                when (val ps = profileState) {
                    ProfileState.Loading -> Unit
                    is ProfileState.Ready ->
                        if (ps.profile == null) OnboardingScreen(container, s.units)
                        else MainShell(container, s)
                }
                ToastHost(toaster, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(c) as T
}

private val tabs = listOf(
    TabItem("home", "Home", Icons.Rounded.Home),
    TabItem("train", "Train", Icons.Rounded.FitnessCenter),
    TabItem("exercises", "Exercises", Icons.Rounded.SportsGymnastics),
    TabItem("log", "Log", Icons.Rounded.ViewTimeline),
    TabItem("me", "Me", Icons.Rounded.Person),
)

@Composable
private fun MainShell(container: AppContainer, s: AppSettings) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var lastSheet by remember { mutableStateOf<Sheet?>(null) }
    if (sheet != null) lastSheet = sheet
    val nav = remember { Nav() }
    val dashVm: DashboardViewModel = viewModel(factory = remember { VmFactory(container) })
    val dash by dashVm.state.collectAsStateWithLifecycle()
    val bottomPad = 120
    val top = nav.stack.lastOrNull()
    val backdrop = LocalBackdrop.current

    BackHandler(enabled = top != null) { nav.pop() }

    CompositionLocalProvider(LocalNav provides nav) {
        val contentLayer = rememberGraphicsLayer()
        backdrop.contentLayer = contentLayer
        Box(Modifier.fillMaxSize()) {
          // tab content is recorded into a layer so the dock's glass can show it (blurred) underneath
          Box(Modifier.fillMaxSize().drawWithContent {
              contentLayer.record { this@drawWithContent.drawContent() }
              drawLayer(contentLayer)
          }) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(spring(0.8f, 300f), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.02f))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> DashboardScreen(dash, container, { sheet = it }, bottomPad)
                    1 -> TrainScreen(container, bottomPad)
                    2 -> ExercisesScreen(container, bottomPad)
                    3 -> TimelineScreen(container, { sheet = it }, bottomPad)
                    else -> MeScreen(container, { sheet = it }, bottomPad)
                }
            }
          }
            AnimatedVisibility(top == null, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                LiquidTabBar(
                    items = tabs, selected = tab, onSelect = { tab = it },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                )
            }
            // quick add: floating glass orb, top-right on every tab
            AnimatedVisibility(
                top == null,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 10.dp, end = 16.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                QuickAddOrb { sheet = Sheet.QuickAdd }
            }

            // full-screen overlays (Gym Mode, details, editors) — each sits on its own copy of the backdrop
            AnimatedContent(
                targetState = top,
                transitionSpec = {
                    (slideInVertically(spring(0.85f, 320f)) { it / 6 } + fadeIn(tween(220)))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "overlay",
            ) { o ->
                if (o != null) Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val l = backdrop.layer
                        if (l != null) drawLayer(l)
                        else drawBackdrop(backdrop.theme, backdrop.image, backdrop.time.floatValue, size.width, size.height)
                    }
                    when (o) {
                        is Overlay.Gym -> GymModeScreen(container, o.workoutId)
                        is Overlay.FinishWorkout -> FinishWorkoutScreen(container, o.workoutId)
                        is Overlay.ExerciseDetail -> ExerciseDetailScreen(container, o.exerciseId)
                        is Overlay.ExerciseEditor -> ExerciseEditorScreen(container, o.exerciseId)
                        is Overlay.TemplateEditor -> TemplateEditorScreen(container, o.templateId)
                        is Overlay.WorkoutDetail -> WorkoutDetailScreen(container, o.workoutId)
                        Overlay.Activity -> com.myfit.tracker.ui.activity.ActivityScreen(container)
                        Overlay.PipChat -> com.myfit.tracker.ui.pip.PipChatScreen(container)
                        Overlay.Archive -> com.myfit.tracker.ui.exercises.ArchiveScreen(container)
                    }
                }
            }

            GlassSheet(visible = sheet != null, onDismiss = { sheet = null }) {
                // keep showing the last content while the exit animation runs
                val shown = sheet ?: lastSheet
                if (shown != null) {
                    AnimatedContent(shown, transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(120))) }, label = "sheet") { sh ->
                        androidx.compose.foundation.layout.Column {
                            EntrySheetContent(sh, container) { next -> sheet = next }
                        }
                    }
                }
            }
        }
    }
}

/** Top-right quick-add button: a glass orb with a glossy accent core. */
@Composable
private fun QuickAddOrb(onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(
        Modifier.size(52.dp).shadow(14.dp, CircleShape, ambientColor = th.accent, spotColor = th.accent),
        shape = CircleShape, blur = 16.dp, onClick = onClick, pressScale = 0.86f,
        tint = th.accent.copy(alpha = 0.55f),
    ) {
        Box(
            Modifier.matchParentSize().drawBehind {
                drawCircle(Brush.verticalGradient(listOf(th.accentBright.copy(alpha = 0.85f), th.accent.copy(alpha = 0.7f))), radius = size.minDimension * 0.40f)
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), 0f, size.height * 0.5f), radius = size.minDimension * 0.40f)
                drawCircle(Color.White.copy(alpha = 0.5f), radius = size.minDimension * 0.40f, style = Stroke(1.dp.toPx()))
            }
        )
        Icon(Icons.Rounded.Add, "Quick add", tint = th.onAccent, modifier = Modifier.align(Alignment.Center).size(28.dp))
    }
}
