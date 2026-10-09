package com.myfit.tracker.ui.coach

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.PipVoice
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.repo.WorkoutExerciseView
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.FormGuide
import com.myfit.tracker.domain.Say
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.gym.Draft
import com.myfit.tracker.ui.gym.GymViewModel
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class Stage { INTRO, SETUP, COUNTDOWN, WORK, DONE, REST }

/**
 * Pro trainer, one exercise at a time: the coach explains setup and joint angles, checks your camera framing,
 * counts you in, paces or counts every rep (scoring each one on depth, tempo and alignment), pushes you at the end,
 * summarises the set, saves it and coaches your rest.
 */
@Composable
fun CoachSetOverlay(cur: WorkoutExerciseView, list: List<WorkoutExerciseView>, vm: GymViewModel, container: AppContainer, onClose: () -> Unit) {
    val th = LocalFitTheme.current
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    remember { Coach.load(ctx); 0 }
    val cp by Coach.prefs.collectAsState()
    val voice = container.pipVoice
    val speaking by voice.speaking.collectAsState()
    val ex = cur.exercise
    val pattern = remember(ex.id) { FormGuide.of(ex.name, ex.primaryMuscle, ex.measurementType) }
    val cues = remember(pattern) { FormGuide.cues(pattern) }
    val m = ex.measurementType
    val timed = m == MeasurementType.DURATION || m == MeasurementType.WEIGHT_DURATION || m == MeasurementType.DISTANCE_DURATION || FormGuide.cameraHolds(pattern)
    val target = vm.targets[ex.id]
    val d = vm.drafts[cur.we.id] ?: Draft()
    val goalReps = (d.reps ?: target?.targetRepsMax ?: target?.targetRepsMin ?: 10).coerceIn(1, 60)
    val goalSec = (d.durationSec ?: target?.targetDurationSec ?: 30L).coerceIn(5L, 3600L)
    val totalSets = target?.targetSets ?: 3
    val setNo = cur.sets.size + 1
    val camOk = FormGuide.cameraCounts(pattern) || FormGuide.cameraHolds(pattern)
    val weightText = d.weightKg?.takeIf { it > 0 && m == MeasurementType.WEIGHT_REPS }?.let { Fmt.weight(it, s.units.weight, 1) }
    val persona = PipVoice.Persona(cp.look.male)

    var stage by remember(cur.we.id) { mutableStateOf(Stage.INTRO) }
    var caption by remember { mutableStateOf<Say?>(null) }
    var reps by remember(cur.we.id) { mutableIntStateOf(0) }
    var elapsed by remember(cur.we.id) { mutableLongStateOf(0L) }
    var phaseIdx by remember { mutableIntStateOf(-1) }
    val phaseProg = remember { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }
    var useCam by remember(cur.we.id) { mutableStateOf(cp.camera && camOk) }
    var hasCamPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCamPerm = ok
        if (!ok) { useCam = false; toaster.show("Camera off — I'll pace you with voice and timing instead") }
    }
    var angle by remember { mutableFloatStateOf(-1f) }
    var restLeft by remember { mutableIntStateOf(0) }
    var cueIdx by remember { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf<FormGuide.Setup?>(null) }
    var readySince by remember { mutableLongStateOf(0L) }
    var lastScore by remember { mutableStateOf<FormGuide.Counter.Event.Rep?>(null) }
    var summary by remember { mutableStateOf<Triple<Int, Int, FormGuide.Fix?>?>(null) }   // avg score, best, top fault
    var counter by remember(cur.we.id) { mutableStateOf(FormGuide.Counter(pattern)) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { voice.stop(); runCatching { tone?.release() } } }
    val camOn = useCam && hasCamPerm

    fun say(line: Say, speak: Boolean = true, interrupt: Boolean = true) {
        caption = line
        if (!cp.voice || !speak) return
        if (!interrupt && voice.speaking.value) return
        voice.speak(line.en, ur = if (cp.urdu) line.ur else null, force = true, persona = persona)
    }

    fun finishSet() {
        if (stage != Stage.WORK) return
        summary = if (counter.scores.isNotEmpty()) Triple(counter.average(), counter.scores.maxOf { it.score }, counter.topFault()) else null
        stage = Stage.DONE
        say(if (timed) FormGuide.Lines.TIME else FormGuide.Lines.setDone(reps))
    }

    BackHandler { if (stage == Stage.WORK) paused = !paused else onClose() }

    // ---------------------------------------------------------------- stage scripts
    LaunchedEffect(cur.we.id, stage) {
        when (stage) {
            Stage.INTRO -> {
                delay(350)
                val intro = FormGuide.Lines.intro(ex.name, setNo, FormGuide.Lines.target(if (timed) null else goalReps, if (timed) goalSec else null, weightText))
                val first = if (setNo == 1) cues.take(2) else listOf(cues[(setNo - 1) % cues.size])
                val all = listOf(intro) + first
                say(Say(all.joinToString(" ") { it.en }, all.joinToString(" ") { it.ur }))
            }
            Stage.SETUP -> {
                readySince = 0L
                val want = FormGuide.requiredView(pattern)
                say(when (want) {
                    FormGuide.View.SIDE -> Say("Prop your phone at hip height, two to three metres away, and stand side-on.", "فون کو کولہے کی اونچائی پر دو تین میٹر دور رکھیں اور سائیڈ سے کھڑے ہوں۔")
                    FormGuide.View.FRONT -> Say("Prop your phone at hip height, two to three metres away, and face it.", "فون کو کولہے کی اونچائی پر دو تین میٹر دور رکھیں اور اس کی طرف منہ کریں۔")
                    else -> Say("Prop your phone two to three metres away so I can see all of you.", "فون کو دو تین میٹر دور رکھیں تاکہ میں آپ کو پورا دیکھ سکوں۔")
                })
            }
            Stage.COUNTDOWN -> {
                say(FormGuide.Lines.COUNTDOWN)
                delay(3000)
                reps = 0; elapsed = 0; phaseIdx = -1; lastScore = null; summary = null
                counter = FormGuide.Counter(pattern)
                stage = Stage.WORK
            }
            Stage.WORK -> {
                if (timed) {
                    var halfSaid = false; var tenSaid = false
                    while (isActive && stage == Stage.WORK) {
                        delay(250)
                        if (paused) continue
                        elapsed += 250
                        val left = goalSec * 1000 - elapsed
                        if (!halfSaid && elapsed >= goalSec * 500 && goalSec >= 20) { halfSaid = true; say(FormGuide.Lines.HALF, interrupt = false) }
                        if (!tenSaid && left <= 10_000 && goalSec >= 25) { tenSaid = true; say(FormGuide.Lines.TEN_LEFT) }
                        if (left <= 0) { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200); finishSet() }
                    }
                } else if (!camOn) {
                    val phases = FormGuide.tempo(pattern).ifEmpty { listOf(FormGuide.Phase(FormGuide.UP, 0.8f), FormGuide.Phase(FormGuide.DOWN, 0.8f)) }
                    while (isActive && stage == Stage.WORK && reps < goalReps) {
                        for ((i, ph) in phases.withIndex()) {
                            while (paused) delay(150)
                            phaseIdx = i
                            if (i == 0 || i == 1) tone?.startTone(if (i == 0) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2, 70)
                            if (reps < 2 && ph.word != FormGuide.BREATHE) say(ph.word, interrupt = false)
                            phaseProg.snapTo(0f)
                            phaseProg.animateTo(1f, tween((ph.sec * 1000).toInt(), easing = LinearEasing))
                        }
                        reps++
                        val left = goalReps - reps
                        val line = when {
                            left == 1 -> FormGuide.Lines.LAST
                            left == 3 && cp.push != Coach.Push.CALM -> FormGuide.Lines.more(3)
                            reps == 2 && cues.size > 1 -> cues.last()
                            reps % 4 == 0 && left > 1 && cp.push != Coach.Push.CALM -> FormGuide.Lines.cheer(reps / 4, cp.push == Coach.Push.HARD)
                            else -> FormGuide.Lines.count(reps)
                        }
                        val n = FormGuide.Lines.count(reps)
                        say(if (line == n) n else Say("$reps. ${line.en}", "${n.ur}۔ ${line.ur}"), interrupt = line != n)
                    }
                    if (reps >= goalReps) { delay(400); finishSet() }
                }
            }
            Stage.DONE -> {}
            Stage.REST -> {
                val rest = container.restTimer.state
                val sec = rest?.let { ((it.endsAt - System.currentTimeMillis()) / 1000).toInt() } ?: (target?.restSeconds ?: s.restDefaultSec)
                restLeft = sec.coerceAtLeast(0)
                say(FormGuide.Lines.rest(restLeft))
                var tenSaid = false
                while (isActive && stage == Stage.REST && restLeft > 0) {
                    delay(1000)
                    restLeft = container.restTimer.state?.let { ((it.endsAt - System.currentTimeMillis()) / 1000).toInt() } ?: (restLeft - 1)
                    if (!tenSaid && restLeft in 1..10) { tenSaid = true; say(FormGuide.Lines.REST_10) }
                }
                if (stage == Stage.REST) stage = Stage.INTRO
            }
        }
    }

    val pose = when (stage) {
        Stage.INTRO -> if (speaking) (if (cueIdx % 2 == 0) "clipboard" else "demo") else "stand"
        Stage.SETUP -> "point"
        Stage.COUNTDOWN -> "point"
        Stage.WORK -> if (reps >= goalReps - 2 && !timed) "cheer" else "point"
        Stage.DONE -> "cheer"
        Stage.REST -> "rest"
    }

    // ---------------------------------------------------------------- layout
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom)))) {
        // ambient stage glow
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.38f), radius = size.width),
                radius = size.width, center = Offset(size.width * 0.5f, size.height * 0.38f))
        }
        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Duo.Close, onClose, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("with ${cp.name}", style = FitType.caption, color = th.accentBright, maxLines = 1)
                }
                SetDots(setNo, totalSets)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(if (cp.voice) Duo.VolumeUp else Duo.VolumeOff, { Coach.update(ctx) { it.copy(voice = !it.voice) }; if (cp.voice) voice.stop() }, size = 40.dp)
            }
            // mode + language row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassChip("Voice & tempo", !useCam, { useCam = false; Coach.update(ctx) { it.copy(camera = false) } }, icon = Duo.Timer)
                GlassChip("Camera", useCam, {
                    if (!camOk) toaster.show("Camera can't follow this exercise yet — I'll pace you with voice instead")
                    else { useCam = true; Coach.update(ctx) { it.copy(camera = true) }; if (!hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA) }
                }, icon = Duo.Camera)
                Spacer(Modifier.weight(1f))
                GlassChip(if (cp.urdu) "اردو" else "EN", cp.urdu, { Coach.update(ctx) { it.copy(urdu = !it.urdu) } })
            }
            Spacer(Modifier.height(8.dp))

            // ---------------- stage
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                val wide = maxWidth > 340.dp
                val showCam = camOn && stage in listOf(Stage.SETUP, Stage.COUNTDOWN, Stage.WORK)
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(th.text.copy(alpha = 0.04f)).border(1.dp, th.text.copy(alpha = 0.08f), RoundedCornerShape(30.dp))) {
                    if (showCam) {
                        val rule = FormGuide.rule(pattern)
                        val hot = rule?.joints?.flatMap { listOf(it, it + 1) }?.toSet() ?: setOf(11, 12, 23, 24, 27, 28)
                        PoseCamera(cp.frontCamera, th.accentBright, hot, { p, now ->
                            when (stage) {
                                Stage.SETUP -> {
                                    val su = FormGuide.setup(p, pattern)
                                    setup = su
                                    if (su.ready) {
                                        if (readySince == 0L) readySince = now
                                        if (now - readySince > 1500) { readySince = 0L; say(Say("Perfect. Hold that spot.", "بہترین۔ یہیں رہیں۔")); stage = Stage.COUNTDOWN }
                                    } else readySince = 0L
                                }
                                Stage.WORK -> if (!paused) counter.feed(p, now).forEach { e ->
                                    when (e) {
                                        is FormGuide.Counter.Event.Angle -> angle = e.deg
                                        is FormGuide.Counter.Event.Cue -> say(e.fix.say)
                                        is FormGuide.Counter.Event.Rep -> {
                                            reps = e.n; lastScore = e
                                            val left = goalReps - e.n
                                            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                                            when {
                                                !timed && left <= 0 -> scope.launch { delay(500); finishSet() }
                                                left == 1 -> say(FormGuide.Lines.LAST)
                                                counter.slowing() && cp.push != Coach.Push.CALM -> say(FormGuide.Lines.SLOWING)
                                                left == 3 && cp.push != Coach.Push.CALM -> say(FormGuide.Lines.more(3))
                                                else -> say(FormGuide.Lines.count(e.n), interrupt = false)
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }, Modifier.fillMaxSize())
                        if (stage == Stage.WORK && angle >= 0 && !timed) Text(if (angle < 2f) Fmt.trim(angle.toDouble(), 2) else "${angle.toInt()}°", style = FitType.title, color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        if (stage == Stage.SETUP) SetupChecklist(setup, pattern, Modifier.align(Alignment.TopStart).padding(12.dp))
                        // small coach in the corner while the camera runs
                        CoachPortrait(cp.look.id, speaking, 58.dp, Modifier.align(Alignment.BottomEnd).padding(12.dp), voice.level)
                    } else when (stage) {
                        Stage.WORK -> Box(Modifier.fillMaxSize()) {
                            ExerciseImage(ex, Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(24.dp)), animate = true, periodMs = 900)
                            CoachPortrait(cp.look.id, speaking, 58.dp, Modifier.align(Alignment.BottomEnd).padding(12.dp), voice.level)
                        }
                        Stage.INTRO -> Row(Modifier.fillMaxSize()) {
                            CoachFigure(cp.look.id, pose, Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp), voice.level)
                            if (wide) Column(Modifier.weight(0.9f).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.Center) {
                                ExerciseImage(ex, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)), animate = true, periodMs = 1000)
                                Spacer(Modifier.height(10.dp))
                                Text(if (timed) "$goalSec s hold" else "$goalReps reps" + (weightText?.let { " · $it" } ?: ""), style = FitType.label, color = th.text)
                                FormGuide.tempo(pattern).takeIf { it.isNotEmpty() }?.let { tp -> Caption("Tempo " + tp.filter { it.word != FormGuide.BREATHE }.joinToString(" · ") { "${it.word.text(cp.urdu)} ${Fmt.trim(it.sec.toDouble(), 1)}s" }) }
                            }
                        }
                        Stage.DONE -> Row(Modifier.fillMaxSize()) {
                            CoachFigure(cp.look.id, pose, Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp), voice.level)
                            SetSummary(reps, timed, elapsed, summary, Modifier.weight(1f).fillMaxHeight().padding(12.dp))
                        }
                        else -> CoachFigure(cp.look.id, pose, Modifier.fillMaxSize().padding(top = 8.dp), voice.level)
                    }
                    // counters / overlays
                    if (stage == Stage.WORK || stage == Stage.REST) {
                        Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            BigCounter(stage, reps, goalReps, timed, elapsed, goalSec, restLeft, phaseIdx, pattern, phaseProg.value, cp.urdu)
                        }
                    }
                    Pop(stage == Stage.WORK && lastScore != null, Modifier.align(Alignment.TopStart).padding(12.dp)) { lastScore?.let { RepScoreChip(it) } }
                    Pop(stage == Stage.COUNTDOWN, Modifier.align(Alignment.Center)) { CountdownNumbers() }
                    if (paused) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text(if (cp.urdu) "رکا ہوا" else "PAUSED", style = FitType.hero, color = Color.White)
                    }
                }
            }

            // ---------------- speech bubble
            SpeechBubble(caption?.text(cp.urdu) ?: if (cp.urdu) "تیار؟" else "Ready when you are!", cp.urdu, speaking,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp))

            // ---------------- controls
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (stage) {
                    Stage.INTRO -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(if (cp.urdu) "ٹپ" else "Form tip", { say(cues[cueIdx % cues.size]); cueIdx++ }, Modifier.weight(1f), icon = Duo.Info, height = 50.dp)
                            AccentButton(if (setNo > totalSets) "Extra set" else "Start set $setNo", {
                                if (useCam && !hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA)
                                stage = if (camOn) Stage.SETUP else Stage.COUNTDOWN
                            }, Modifier.weight(1.4f), icon = Duo.PlayArrow, height = 50.dp)
                        }
                        if (setNo > totalSets) {
                            val next = list.getOrNull(list.indexOfFirst { it.we.id == cur.we.id } + 1)
                            if (next != null) GlassButton("Next: ${next.exercise.name}", { vm.select(next.we.id) }, Modifier.fillMaxWidth(), icon = Duo.ArrowForward, height = 46.dp)
                            else GlassButton("All done — back to workout", { say(FormGuide.Lines.WORKOUT_DONE); onClose() }, Modifier.fillMaxWidth(), icon = Duo.Check, height = 46.dp)
                        }
                    }
                    Stage.SETUP -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Back", { stage = Stage.INTRO }, Modifier.weight(1f), height = 50.dp)
                        AccentButton("Start anyway", { stage = Stage.COUNTDOWN }, Modifier.weight(1.4f), icon = Duo.PlayArrow, height = 50.dp)
                    }
                    Stage.COUNTDOWN -> GlassButton("Cancel", { stage = Stage.INTRO }, Modifier.fillMaxWidth(), height = 50.dp)
                    Stage.WORK -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(if (paused) "Resume" else "Pause", { paused = !paused }, Modifier.weight(1f), icon = if (paused) Duo.PlayArrow else Duo.Pause, height = 54.dp)
                        AccentButton("Finish set", { finishSet() }, Modifier.weight(1.3f), icon = Duo.Check, height = 54.dp)
                    }
                    Stage.DONE -> {
                        if (!timed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            GlassIconButton(Duo.Remove, { reps = (reps - 1).coerceAtLeast(0) })
                            Text("$reps reps", style = FitType.title, color = th.text, modifier = Modifier.padding(horizontal = 18.dp))
                            GlassIconButton(Duo.Add, { reps += 1 })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Redo", { stage = Stage.INTRO }, Modifier.weight(1f), icon = Duo.Replay, height = 52.dp)
                            AccentButton(if (vm.busy) "Saving…" else "Save set", {
                                vm.update(cur.we.id) { dr -> if (timed) dr.copy(durationSec = (elapsed / 1000).coerceAtLeast(1), source = "Coach") else dr.copy(reps = reps, source = "Coach") }
                                vm.complete(cur, list, s) { res ->
                                    if (res.error != null) toaster.show(res.error)
                                    else { res.beatBest?.let { toaster.show("New best! $it") }; stage = Stage.REST }
                                }
                            }, Modifier.weight(1.4f), icon = Duo.Save, height = 52.dp, enabled = !vm.busy)
                        }
                    }
                    Stage.REST -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("+15 s", { container.restTimer.add(15, s.restSound, s.restVibrate); restLeft += 15 }, Modifier.weight(1f), height = 52.dp)
                        AccentButton("Skip rest", { vm.skipRest(); stage = Stage.INTRO }, Modifier.weight(1.3f), icon = Duo.ArrowForward, height = 52.dp)
                    }
                }
                Caption("Guidance only — stop if anything hurts. Camera frames stay on your phone.", Modifier.fillMaxWidth(), color = th.textFaint)
            }
        }
    }
}

/** Springy show/hide (kept outside any Column/Row scope). */
@Composable
private fun Pop(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = scaleIn(spring(0.5f, 500f)) + fadeIn(), exit = scaleOut() + fadeOut()) { content() }
}

/** Comic-style speech bubble with a tail pointing up at the coach. */
@Composable
private fun SpeechBubble(text: String, rtl: Boolean, speaking: Boolean, modifier: Modifier) {
    val th = LocalFitTheme.current
    val glow by animateFloatAsState(if (speaking) 1f else 0f, label = "bubble")
    val shape = remember {
        GenericShape { sz, _ ->
            val r = 22f * 2.5f; val tailW = 36f; val tailH = 22f; val tx = sz.width * 0.18f
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, tailH, sz.width, sz.height, androidx.compose.ui.geometry.CornerRadius(r)))
            moveTo(tx, tailH + 2f); lineTo(tx + tailW * 0.3f, 0f); lineTo(tx + tailW, tailH + 2f); close()
        }
    }
    Box(modifier.clip(shape).background(th.text.copy(alpha = 0.07f + glow * 0.04f)).border(1.dp, th.accent.copy(alpha = 0.25f + glow * 0.4f), shape)
        .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp).height(62.dp)) {
        AnimatedContent(text, transitionSpec = { (fadeIn(tween(180)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(120)) }, label = "say") { t ->
            Text(t, style = FitType.body.copy(fontSize = 15.sp), color = th.text, textAlign = if (rtl) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
        }
    }
}

@Composable
private fun SetDots(setNo: Int, total: Int) {
    val th = LocalFitTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(th.text.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        (1..maxOf(total, setNo)).take(8).forEach { i ->
            Box(Modifier.size(if (i == setNo) 10.dp else 7.dp).clip(CircleShape).background(when {
                i < setNo -> th.success; i == setNo -> th.accentBright; else -> th.text.copy(alpha = 0.25f)
            }))
        }
    }
}

/** Camera framing checklist: body in view, distance, angle. */
@Composable
private fun SetupChecklist(su: FormGuide.Setup?, pattern: FormGuide.Pattern, modifier: Modifier) {
    val view = FormGuide.requiredView(pattern)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("GET IN POSITION", style = FitType.overline, color = Color.White.copy(alpha = 0.8f))
        CheckLine("Whole body in view", su?.visible == true)
        CheckLine("Good distance", su?.distanceOk == true)
        CheckLine(when (view) { FormGuide.View.SIDE -> "Side-on to the camera"; FormGuide.View.FRONT -> "Facing the camera"; else -> "Any angle" }, su?.viewOk == true || view == FormGuide.View.ANY)
        su?.fix?.let { Text(it.say.en, style = FitType.caption, color = Color(0xFFFFD27A)) }
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Duo.CheckCircle else Duo.RadioButtonUnchecked, null, tint = if (ok) Color(0xFF3BE08F) else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = FitType.caption, color = Color.White)
    }
}

@Composable
private fun RepScoreChip(e: FormGuide.Counter.Event.Rep) {
    val c = when { e.score >= 85 -> Color(0xFF3BE08F); e.score >= 65 -> Color(0xFFFFC542); else -> Color(0xFFFF6B5A) }
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) { Text("${e.score}", style = FitType.label, color = Color.Black) }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Rep ${e.n}", style = FitType.label, color = Color.White)
            Text(e.fault?.say?.en ?: if (e.score >= 85) "Great form" else "Good", style = FitType.caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun SetSummary(reps: Int, timed: Boolean, elapsed: Long, s: Triple<Int, Int, FormGuide.Fix?>?, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text("SET COMPLETE", style = FitType.overline, color = th.accentBright)
        Text(if (timed) "${elapsed / 1000} s" else "$reps reps", style = FitType.hero.copy(fontSize = 40.sp), color = th.text)
        if (s != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(s.first, 64.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Form score", style = FitType.label, color = th.text)
                    Caption("Best rep ${s.second}")
                }
            }
            s.third?.let { Spacer(Modifier.height(8.dp)); Caption("Work on: ${it.say.en.trimEnd('.', '!')}", color = th.warning) }
        } else Caption("Nice work — counted by tempo.")
    }
}

@Composable
private fun ScoreRing(score: Int, size: androidx.compose.ui.unit.Dp) {
    val th = LocalFitTheme.current
    val c = when { score >= 85 -> th.success; score >= 65 -> th.warning; else -> th.danger }
    val a = remember { Animatable(0f) }
    LaunchedEffect(score) { a.animateTo(score / 100f, tween(900)) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 7.dp.toPx()
            drawArc(c.copy(alpha = 0.2f), 0f, 360f, false, Offset(st / 2, st / 2), Size(this.size.width - st, this.size.height - st), style = Stroke(st))
            drawArc(c, -90f, 360f * a.value, false, Offset(st / 2, st / 2), Size(this.size.width - st, this.size.height - st), style = Stroke(st, cap = StrokeCap.Round))
        }
        Text("$score", style = FitType.title, color = th.text)
    }
}

@Composable
private fun CountdownNumbers() {
    var n by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) { while (n > 1) { delay(1000); n-- } }
    AnimatedContent(n, transitionSpec = { scaleIn(spring(0.45f, 400f), initialScale = 2f) + fadeIn() togetherWith scaleOut(targetScale = 0.6f) + fadeOut() }, label = "cd") { v ->
        Text("$v", style = FitType.hero.copy(fontSize = 120.sp), color = Color.White)
    }
}

/** Rep / time ring with the tempo phase word. */
@Composable
private fun BigCounter(stage: Stage, reps: Int, goal: Int, timed: Boolean, elapsed: Long, goalSec: Long, restLeft: Int, phaseIdx: Int, pattern: FormGuide.Pattern, phaseProg: Float, urdu: Boolean) {
    val th = LocalFitTheme.current
    val (big, small, frac) = when {
        stage == Stage.REST -> Triple("${restLeft}s", if (urdu) "آرام" else "Rest", 0f)
        timed -> { val left = (goalSec - elapsed / 1000).coerceAtLeast(0); Triple("${left}s", if (urdu) "رکے رہیں" else "Hold", (elapsed / 1000f / goalSec).coerceIn(0f, 1f)) }
        else -> Triple("$reps", "/ $goal", reps / goal.toFloat())
    }
    val phase = FormGuide.tempo(pattern).getOrNull(phaseIdx)?.word?.text(urdu)
    Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = 7.dp.toPx()
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st))
                drawArc(th.accentBright, -90f, 360f * frac, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
            }
            AnimatedContent(big, transitionSpec = { (scaleIn(initialScale = 1.4f) + fadeIn()) togetherWith fadeOut() }, label = "n") { t ->
                Text(t, style = if (t.length > 4) FitType.label else FitType.title, color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(small, style = FitType.label, color = Color.White.copy(alpha = 0.8f))
            if (stage == Stage.WORK && !timed && phase != null) {
                Text(phase.uppercase(), style = FitType.title, color = th.accentBright)
                Box(Modifier.width(110.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(phaseProg).height(5.dp).background(th.accentBright))
                }
            }
        }
    }
}
