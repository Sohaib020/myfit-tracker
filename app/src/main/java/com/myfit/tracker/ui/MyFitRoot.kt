package com.myfit.tracker.ui

import com.myfit.tracker.ui.theme.Duo

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.myfit.tracker.ui.theme.drawBaked
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
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
import com.myfit.tracker.ui.components.PipTour
import com.myfit.tracker.ui.components.TourStep
import com.myfit.tracker.ui.components.tourTarget
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.onboarding.OnboardingScreen
import com.myfit.tracker.ui.settings.BackgroundImages
import com.myfit.tracker.ui.settings.MeScreen
import com.myfit.tracker.ui.settings.SettingsScreen
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
    // Themes are STILL images by default: rendered once (plus their two blurred versions) whenever the
    // theme, wallpaper, screen size or blur settings change — zero GPU work per frame after that.
    // A few themes may drift very gently (4 updates/s) if "Gentle motion" is on.
    val gentle = false   // every theme is a still image
    LaunchedEffect(theme.id) { backdrop.time.floatValue = theme.stillT }
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
                val dens = androidx.compose.ui.platform.LocalDensity.current
                val cardBlurPx = with(dens) { (26.dp * s.blurAmount).toPx() }
                val dockBlurPx = with(dens) { (30.dp * s.dockBlur).toPx() }
                val blurOk = android.os.Build.VERSION.SDK_INT >= 31 && !com.myfit.tracker.CrashGuard.safeMode
                val gfx = androidx.compose.ui.platform.LocalGraphicsContext.current
                // ---- still path: bake three small bitmaps (theme, card blur, dock blur)
                LaunchedEffect(theme.id, backdrop.image, backdrop.rootSize, cardBlurPx, dockBlurPx, gentle, blurOk) {
                    if (gentle) { backdrop.bgImg = null; backdrop.cardImg = null; backdrop.dockImg = null; return@LaunchedEffect }
                    val full = backdrop.rootSize
                    if (full.width < 2f || full.height < 2f) return@LaunchedEffect
                    runCatching { com.myfit.tracker.ui.theme.BackdropBaker.bake(gfx, dens, theme, backdrop.image, full, cardBlurPx, dockBlurPx, blurOk) }
                        .onSuccess { (bg, card, dock) -> backdrop.bgImg = bg; backdrop.cardImg = card; backdrop.dockImg = dock }
                }
                // ---- gentle path: small live layers, updated only when `time` ticks (4×/s)
                val small = rememberGraphicsLayer()
                val smallCard = rememberGraphicsLayer()
                val smallDock = rememberGraphicsLayer()
                val bgLayer = rememberGraphicsLayer()
                val blurLayer = rememberGraphicsLayer()
                val dockLayer = rememberGraphicsLayer()
                backdrop.layer = if (gentle) bgLayer else null
                backdrop.blurLayer = if (gentle && blurOk) blurLayer else null
                backdrop.dockLayer = if (gentle && blurOk) dockLayer else null
                Canvas(Modifier.fillMaxSize()) {
                    val img = backdrop.bgImg
                    if (!gentle) {
                        if (img != null) drawBaked(img, size)
                        else drawBackdrop(backdrop.theme, backdrop.image, theme.stillT, size.width, size.height)
                        return@Canvas
                    }
                    val t = backdrop.time.floatValue
                    val k = 3f
                    val sw = (size.width / k).coerceAtLeast(1f); val sh = (size.height / k).coerceAtLeast(1f)
                    val smallSize = androidx.compose.ui.unit.IntSize(kotlin.math.ceil(sw).toInt(), kotlin.math.ceil(sh).toInt())
                    small.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    small.record(smallSize) { drawBackdrop(backdrop.theme, backdrop.image, t, sw, sh) }
                    bgLayer.record { scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawLayer(small) } }
                    drawLayer(bgLayer)
                    if (blurOk) {
                        smallCard.renderEffect = if (cardBlurPx / k > 0.5f) androidx.compose.ui.graphics.BlurEffect(cardBlurPx / k, cardBlurPx / k, androidx.compose.ui.graphics.TileMode.Clamp) else null
                        smallCard.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                        smallCard.record(smallSize) { drawLayer(small) }
                        blurLayer.record { scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawLayer(smallCard) } }
                        smallDock.renderEffect = if (dockBlurPx / k > 0.5f) androidx.compose.ui.graphics.BlurEffect(dockBlurPx / k, dockBlurPx / k, androidx.compose.ui.graphics.TileMode.Clamp) else null
                        smallDock.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                        smallDock.record(smallSize) { drawLayer(small) }
                        dockLayer.record { scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawLayer(smallDock) } }
                    }
                }
                when (val ps = profileState) {
                    ProfileState.Loading -> Unit
                    is ProfileState.Ready -> {
                        val socialOn = remember { container.social.start(); container.social.available }
                        val account by container.social.user.collectAsState()
                        val obCtx = androidx.compose.ui.platform.LocalContext.current
                        var catchUp by remember(ps.profile == null) { mutableStateOf(if (ps.profile == null) emptyList() else com.myfit.tracker.ui.onboarding.OnboardingVersion.pendingSteps(obCtx)) }
                        if (socialOn && account == null) com.myfit.tracker.ui.social.SignInGate(container)
                        else if (ps.profile == null) OnboardingScreen(container, s.units)
                        else if (catchUp.isNotEmpty()) com.myfit.tracker.ui.onboarding.OnboardingCatchUp(container, ps.profile, s.units, catchUp) { catchUp = emptyList() }
                        else if (!s.permsAsked) com.myfit.tracker.ui.onboarding.PermissionsScreen(container)
                        else MainShell(container, s)
                    }
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
    TabItem("home", "Home", Duo.Home),
    TabItem("train", "Train", Duo.FitnessCenter),
    TabItem("food", "Food", Duo.ForkKnife),
    TabItem("arena", "Arena", Duo.EmojiEvents),
    TabItem("settings", "Settings", Duo.Gear),
)

@Composable
private fun MainShell(container: AppContainer, s: AppSettings) {
    com.myfit.tracker.domain.EnergyUnit.label = s.units.energy.label
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
          Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // quick cross-fade (like iOS tab bars): no scaling, so heavy tabs never stutter
                    fadeIn(tween(160, delayMillis = 40)).togetherWith(fadeOut(tween(90)))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> DashboardScreen(dash, container, { sheet = it }, bottomPad, goTab = { tab = it })
                    1 -> com.myfit.tracker.ui.train.TrainHost(container, bottomPad)
                    2 -> com.myfit.tracker.ui.food.FoodDiaryScreen(container, null, asTab = true, bottomPad = bottomPad)
                    3 -> com.myfit.tracker.ui.arena.ArenaScreen(container, bottomPad)
                    else -> SettingsScreen(container, { sheet = it }, bottomPad)
                }
            }
          }
            // bottom gradient scrim behind the dock (content fades out instead of colliding with it)
            // chrome (scrim, dock, Me, calendar, +) hides while a sheet or full-screen panel is open
            val chrome = top == null && com.myfit.tracker.ui.components.SheetsOpen.count.intValue == 0
            AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                val th = LocalFitTheme.current
                val base = if (th.isLight) Color.White else Color.Black
                Box(
                    Modifier.fillMaxWidth().height(150.dp).drawBehind {
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = if (th.isLight) 0.55f else 0.45f), base.copy(alpha = if (th.isLight) 0.85f else 0.75f))))
                    },
                )
            }
            AnimatedVisibility(chrome, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                LiquidTabBar(
                    items = tabs, selected = tab, onSelect = { tab = it },
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp).tourTarget("dock"),
                )
            }
            // quick add: floating glass orb, top-right on every tab
            AnimatedVisibility(
                chrome,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 10.dp, end = 16.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    com.myfit.tracker.ui.theme.GlassIconButton(Duo.Person, { nav.push(Overlay.Social) }, size = 50.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    com.myfit.tracker.ui.theme.GlassIconButton(Duo.CalendarMonth, { nav.push(Overlay.History) }, Modifier.tourTarget("cal"), size = 50.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    Box(Modifier.tourTarget("add")) { QuickAddOrb { sheet = Sheet.QuickAdd } }
                }
            }
            // Me: profile, body & targets — top-left on every tab
            AnimatedVisibility(
                chrome,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 10.dp, start = 16.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                Box(Modifier.tourTarget("me")) { MePill(dash.profile?.name ?: "", com.myfit.tracker.ui.social.rememberAccountPhoto(container), com.myfit.tracker.update.rememberUpdateProgress()) { nav.push(Overlay.Me) } }
            }

            // full-screen overlays (Gym Mode, details, editors) — each sits on its own copy of the backdrop
            AnimatedContent(
                targetState = top,
                transitionSpec = {
                    // the new screen grows out of the tapped card (GrowFrom); the old one shrinks away
                    androidx.compose.animation.EnterTransition.None
                        .togetherWith(fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 0.92f))
                },
                label = "overlay",
            ) { o ->
                if (o != null) com.myfit.tracker.ui.components.GrowFrom {
                    val solid = LocalFitTheme.current.bgBottom.copy(alpha = 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(solid)       // nothing from the screen underneath may show through (e.g. behind the nav bar)
                        val l = backdrop.layer
                        val bi = backdrop.bgImg
                        if (bi != null) drawBaked(bi, size)
                        else if (l != null) drawLayer(l)
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
                        Overlay.Today -> com.myfit.tracker.ui.activity.TodayScreen(container)
                        Overlay.Stopwatch -> com.myfit.tracker.ui.activity.StopwatchScreen(container)
                        Overlay.PipChat -> com.myfit.tracker.ui.pip.PipChatScreen(container)
                        Overlay.Archive -> com.myfit.tracker.ui.exercises.ArchiveScreen(container)
                        Overlay.Me -> MeScreen(container) { sheet = it }
                        Overlay.ArrangeDash -> com.myfit.tracker.ui.dashboard.ArrangeDashScreen(container)
                        Overlay.Records -> com.myfit.tracker.ui.exercises.RecordsScreen(container)
                        Overlay.Social -> com.myfit.tracker.ui.social.SocialScreen(container)
                        Overlay.Body -> com.myfit.tracker.ui.body.BodyScreen(container) { sheet = it }
                        Overlay.Badges -> com.myfit.tracker.ui.badges.BadgesScreen(container)
                        Overlay.Cycle -> com.myfit.tracker.ui.cycle.CycleScreen(container)
                        Overlay.Glucose -> com.myfit.tracker.ui.glucose.GlucoseScreen(container)
                        Overlay.Meds -> com.myfit.tracker.ui.glucose.MedsScreen(container)
                        Overlay.Vitals -> com.myfit.tracker.ui.vitals.VitalsScreen(container)
                        Overlay.CameraHr -> com.myfit.tracker.ui.vitals.CameraHeartRateScreen(container)
                        Overlay.Devices -> com.myfit.tracker.ui.vitals.DevicesScreen(container)
                        Overlay.Mind -> com.myfit.tracker.ui.mind.MindScreen(container)
                        Overlay.Reminders -> com.myfit.tracker.ui.routine.RemindersScreen(container)
                        Overlay.Supplements -> com.myfit.tracker.ui.routine.SupplementsScreen(container)
                        Overlay.Fasting -> com.myfit.tracker.ui.routine.FastingScreen(container)
                        Overlay.DevSettings -> com.myfit.tracker.ui.settings.DevSettingsScreen(container)
                        Overlay.HealthHub -> com.myfit.tracker.ui.dashboard.HealthHubScreen(container)
                        is Overlay.PickExercises -> com.myfit.tracker.ui.train.PickExercisesScreen(container, o.templateId, o.workoutId)
                        is Overlay.ProgramDetail -> com.myfit.tracker.ui.programs.ProgramDetailScreen(container, o.id)
                        Overlay.Deen -> com.myfit.tracker.ui.deen.DeenScreen(container)
                        Overlay.History -> TimelineScreen(container, { sheet = it }, 40, onBack = { nav.pop() })
                        is Overlay.DayLog -> com.myfit.tracker.ui.timeline.DayLogScreen(container, o.date) { sheet = it }
                        is Overlay.Food -> com.myfit.tracker.ui.food.FoodDiaryScreen(container, o.date)
                        is Overlay.FoodAdd -> com.myfit.tracker.ui.food.FoodAddScreen(container, o.mealType, o.date, o.tab)
                        is Overlay.FoodPhoto -> com.myfit.tracker.ui.food.FoodPhotoScreen(container, o.mealType, o.date)
                    }
                }
            }

            if (!s.tourDone && top == null) {
                val steps = remember {
                    listOf(
                        TourStep(null, "Hi, I'm Pip!", "Let me show you where everything is. It takes 30 seconds.", PipMood.WAVE) { tab = 0 },
                        TourStep("me", "You", "Your profile, online account, body numbers and daily targets."),
                        TourStep("cal", "Daily log", "Everything you recorded, grouped by type. Tap a day to look back."),
                        TourStep("add", "Quick add", "Water, weight, sleep, food, a note — log anything from any tab.", PipMood.EXCITED),
                        TourStep("card_RINGS", "Your Home cards", "Tap a card to open it. Hold it to move, resize or hide it. Buttons on cards work without opening them."),
                        TourStep("tab:1", "Train", "Workouts, templates and the exercise library. Tap + on any exercise to add it."),
                        TourStep("tab:2", "Food", "Your food diary, meal snaps, fasting and supplements."),
                        TourStep("tab:3", "Arena", "Weekly & monthly challenges, journeys, duels, games and friends. Only device-recorded activity counts."),
                        TourStep("tab:4", "Settings", "Themes, reminders, health connections and privacy."),
                        TourStep(null, "That's it!", "Tap me on Home any time to chat. Let's get moving!", PipMood.CELEBRATE),
                    )
                }
                PipTour(steps) { container.write { container.settings.setTourDone(true) } }
            }
            LaunchedEffect(Unit) { com.myfit.tracker.domain.BadgeEngine.refresh(container, force = true) }
            // self-update from GitHub releases (checks on launch; downloads on Wi-Fi; user taps Install)
            LaunchedEffect(Unit) { runCatching { com.myfit.tracker.update.AppUpdater.autoRun(container.app) } }
            if (top == null) com.myfit.tracker.update.UpdateIsland(androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            // friend invite links (myfit://invite?c=CODE): add the friend once signed in, then show Arena → Friends
            val inviteToaster = LocalToaster.current
            val inviteCode by com.myfit.tracker.social.Invite.pending.collectAsState()
            val socialUser by container.social.user.collectAsState()
            LaunchedEffect(inviteCode, socialUser) {
                val code = inviteCode ?: return@LaunchedEffect
                if (socialUser == null || !container.social.available) { inviteToaster.show("Sign in (Me → Account) to accept the invite · code $code"); return@LaunchedEffect }
                runCatching { container.social.addFriendByCode(code) }
                    .onSuccess { inviteToaster.show("You and ${it.name} are now friends! 🎉"); nav.push(Overlay.Social) }
                    .onFailure { inviteToaster.show(it.message ?: "Couldn't add that friend") }
                com.myfit.tracker.social.Invite.pending.value = null
            }
            // queue Pip's offline brain once after install: Wi-Fi only, resumable, in the background
            LaunchedEffect(Unit) {
                runCatching {
                    val ai = com.myfit.tracker.ai.ondevice.OnDeviceAi.get(container.app)
                    val spec = ai.models.spec()
                    val ok = com.myfit.tracker.ai.ondevice.DeviceCheck.check(container.app, spec).tier != com.myfit.tracker.ai.ondevice.DeviceCheck.Tier.UNSUPPORTED
                    if (ok && !ai.models.isInstalled(spec) && ai.prefs.takeAutoDownload()) ai.models.start()
                }
            }
            com.myfit.tracker.ui.badges.BadgeCelebration(container)
            com.myfit.tracker.ui.settings.AiCapSheetHost()
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

/** Top-right quick-add button: a clean, solid accent circle with a soft shadow. */
@Composable
private fun QuickAddOrb(onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.45f, 700f), label = "orb")
    Box(
        Modifier
            .size(50.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .shadow(10.dp, CircleShape, ambientColor = th.accent, spotColor = th.accent)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent)))
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.5f))
            }
            .clickable(src, indication = null) { tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Duo.Add, "Quick add", tint = th.onAccent, modifier = Modifier.size(26.dp))
    }
}

/** Top-left "Me" button: your initial in an accent circle + label, on a glass capsule. */
@Composable
private fun MePill(name: String, photo: androidx.compose.ui.graphics.ImageBitmap?, update: Float? = null, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    com.myfit.tracker.ui.theme.Glass(
        Modifier.height(50.dp), shape = CircleShape, onClick = { tick(); onClick() }, pressScale = 0.92f,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(start = 5.dp, end = 16.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photo != null) androidx.compose.foundation.Image(photo, null, Modifier.size(40.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Box(
                Modifier.size(40.dp).clip(CircleShape).drawBehind { drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent))) },
                contentAlignment = Alignment.Center,
            ) {
                val ini = name.trim().take(1).uppercase()
                if (ini.isNotEmpty()) androidx.compose.material3.Text(ini, style = com.myfit.tracker.ui.theme.FitType.section, color = th.onAccent)
                else Icon(Duo.Person, null, tint = th.onAccent, modifier = Modifier.size(22.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            androidx.compose.material3.Text("Me", style = com.myfit.tracker.ui.theme.FitType.section, color = th.text)
            // an update tucked into the pill: progress ring + arrow (green check ring when ready)
            androidx.compose.animation.AnimatedVisibility(update != null, enter = androidx.compose.animation.expandHorizontally() + fadeIn(), exit = androidx.compose.animation.shrinkHorizontally() + fadeOut()) {
                val u = update ?: 1f
                val anim by androidx.compose.animation.core.animateFloatAsState(u, label = "upd")
                Box(Modifier.padding(start = 10.dp).size(26.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(th.text.copy(alpha = 0.15f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                        drawArc(Color(0xFF3DDC84), -90f, 360f * anim, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Icon(if (u >= 1f) Duo.Check else Duo.ArrowDownward, "Update", tint = th.text, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
