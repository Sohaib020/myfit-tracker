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
    LaunchedEffect(s.animatedBackground) {
        if (!s.animatedBackground) return@LaunchedEffect
        var last = -1L
        while (true) withFrameMillis { now ->
            if (last >= 0) backdrop.time.floatValue += (now - last) / 1000f
            last = now
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
                Canvas(Modifier.fillMaxSize()) {
                    drawBackdrop(backdrop.theme, backdrop.image, backdrop.time.floatValue, size.width, size.height)
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
        Box(Modifier.fillMaxSize()) {
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
            AnimatedVisibility(top == null, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                LiquidTabBar(
                    items = tabs, selected = tab, onSelect = { tab = it }, onQuickAdd = { sheet = Sheet.QuickAdd },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 10.dp),
                )
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
                        drawBackdrop(backdrop.theme, backdrop.image, backdrop.time.floatValue, size.width, size.height)
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
