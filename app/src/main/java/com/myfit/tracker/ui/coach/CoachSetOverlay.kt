package com.myfit.tracker.ui.coach

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class Stage { INTRO, COUNTDOWN, WORK, DONE, REST }

/**
 * Pro trainer: runs the current exercise set by set — explains setup and joint angles, counts you in, paces each rep
 * (or counts them from the camera and corrects your form), pushes you near the end, saves the set and coaches the rest.
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
    val timed = m == MeasurementType.DURATION || m == MeasurementType.WEIGHT_DURATION || m == MeasurementType.DISTANCE_DURATION || pattern == FormGuide.Pattern.PLANK
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
    val counter = remember(cur.we.id, stage == Stage.WORK) { FormGuide.Counter(pattern) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { voice.stop(); runCatching { tone?.release() } } }

    fun say(line: Say, speak: Boolean = true, interrupt: Boolean = true) {
        caption = line
        if (!cp.voice || !speak) return
        if (!interrupt && voice.speaking.value) return
        voice.speak(line.en, ur = if (cp.urdu) line.ur else null, force = true, persona = persona)
    }

    fun finishSet() {
        if (stage != Stage.WORK) return
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
            Stage.COUNTDOWN -> {
                say(FormGuide.Lines.COUNTDOWN)
                delay(3000)
                reps = 0; elapsed = 0; phaseIdx = -1
                stage = Stage.WORK
                if (useCam && hasCamPerm) delay(400).also { say(if (cp.look.male) FormGuide.Lines.CAM_START else FormGuide.Lines.CAM_START_F, interrupt = false) }
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
                } else if (!(useCam && hasCamPerm)) {
                    // voice & tempo: pace each rep with the tempo phases
                    val phases = FormGuide.tempo(pattern)
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

    // ---------------------------------------------------------------- layout
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(th.bgTop, th.bgBottom)))) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Duo.Close, onClose)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(cp.name, style = FitType.label, color = th.accentBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${ex.name} · set $setNo of $totalSets", style = FitType.caption, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassChip(if (cp.urdu) "اردو" else "EN", cp.urdu, { Coach.update(ctx) { it.copy(urdu = !it.urdu) } })
                Spacer(Modifier.width(6.dp))
                GlassIconButton(if (cp.voice) Duo.VolumeUp else Duo.VolumeOff, { Coach.update(ctx) { it.copy(voice = !it.voice) }; if (cp.voice) voice.stop() }, size = 40.dp)
            }
            // camera / tempo switch
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassChip("Voice & tempo", !useCam, { useCam = false; Coach.update(ctx) { it.copy(camera = false) } }, icon = Duo.Timer)
                GlassChip("Camera", useCam, {
                    if (!camOk) toaster.show("Camera form-check isn't available for this exercise yet — I'll pace you instead")
                    else { useCam = true; Coach.update(ctx) { it.copy(camera = true) }; if (!hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA) }
                }, icon = Duo.Camera)
                if (useCam && hasCamPerm) GlassIconButton(Duo.Sync, { Coach.update(ctx) { it.copy(frontCamera = !it.frontCamera) } }, size = 40.dp)
            }
            Spacer(Modifier.height(8.dp))

            // main visual
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                val wide = maxWidth > 300.dp
                val showCam = useCam && hasCamPerm && (stage == Stage.WORK || stage == Stage.COUNTDOWN || stage == Stage.INTRO)
                Glass(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp)) {
                    if (showCam) {
                        val rule = FormGuide.rule(pattern)
                        PoseCamera(cp.frontCamera, th.accentBright, rule?.let { setOf(it.a, it.b, it.c, it.a + 1, it.b + 1, it.c + 1) } ?: setOf(11, 12, 23, 24, 27, 28), { pose, now ->
                            if (stage != Stage.WORK || paused) return@PoseCamera
                            counter.feed(pose, now).forEach { e ->
                                when (e) {
                                    is FormGuide.Counter.Event.Angle -> angle = e.deg
                                    is FormGuide.Counter.Event.Cue -> say(e.fix.say)
                                    is FormGuide.Counter.Event.Rep -> {
                                        reps = e.n
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
                        }, Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
                        if (angle >= 0) Text("${angle.toInt()}°", style = FitType.title, color = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    } else when (stage) {
                        Stage.WORK -> Box(Modifier.fillMaxSize()) {
                            ExerciseImage(ex, Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(22.dp)), animate = true, periodMs = 900)
                        }
                        Stage.DONE -> CoachFigure(cp.look.id, "cheer", Modifier.fillMaxSize().padding(top = 8.dp))
                        Stage.INTRO -> Row(Modifier.fillMaxSize()) {
                            CoachFigure(cp.look.id, if (speaking) "demo" else "stand", Modifier.weight(1f).fillMaxSize().padding(top = 8.dp))
                            if (wide) ExerciseImage(ex, Modifier.weight(0.8f).align(Alignment.CenterVertically).aspectRatio(1f).padding(10.dp).clip(RoundedCornerShape(20.dp)), animate = true, periodMs = 1000)
                        }
                        else -> CoachFigure(cp.look.id, "stand", Modifier.fillMaxSize().padding(top = 8.dp))
                    }
                    // big counter overlay
                    if (stage == Stage.COUNTDOWN || stage == Stage.WORK || stage == Stage.REST) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) { BigCounter(stage, reps, goalReps, timed, elapsed, goalSec, restLeft, phaseIdx, pattern, phaseProg.value, cp.urdu) }
                    }
                    if (paused) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Text(if (cp.urdu) "رکا ہوا" else "PAUSED", style = FitType.hero, color = Color.White)
                    }
                }
            }

            // coach caption
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CoachPortrait(cp.look.id, speaking, 52.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(th.text.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 8.dp).height(56.dp).verticalScroll(rememberScrollState())) {
                    Text(caption?.text(cp.urdu) ?: if (cp.urdu) "تیار؟" else "Ready when you are.", style = FitType.body, color = th.text,
                        textAlign = if (cp.urdu) TextAlign.End else TextAlign.Start, modifier = Modifier.fillMaxWidth())
                }
            }

            // controls
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (stage) {
                    Stage.INTRO -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(if (cp.urdu) "ٹپ" else "Form tip", { say(cues[cueIdx % cues.size]); cueIdx++ }, Modifier.weight(1f), icon = Duo.Info, height = 48.dp)
                            AccentButton(if (setNo > totalSets) "Extra set" else "Start set $setNo", {
                                if (useCam && !hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA)
                                stage = Stage.COUNTDOWN
                            }, Modifier.weight(1.4f), icon = Duo.PlayArrow, height = 48.dp)
                        }
                        if (setNo > totalSets) {
                            val next = list.getOrNull(list.indexOfFirst { it.we.id == cur.we.id } + 1)
                            if (next != null) GlassButton("Next: ${next.exercise.name}", { vm.select(next.we.id) }, Modifier.fillMaxWidth(), icon = Duo.ArrowForward, height = 46.dp)
                            else GlassButton("All done — back to workout", { say(FormGuide.Lines.WORKOUT_DONE); onClose() }, Modifier.fillMaxWidth(), icon = Duo.Check, height = 46.dp)
                        }
                    }
                    Stage.COUNTDOWN -> GlassButton("Cancel", { stage = Stage.INTRO }, Modifier.fillMaxWidth(), height = 48.dp)
                    Stage.WORK -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(if (paused) "Resume" else "Pause", { paused = !paused }, Modifier.weight(1f), icon = if (paused) Duo.PlayArrow else Duo.Pause, height = 52.dp)
                        AccentButton("Finish set", { finishSet() }, Modifier.weight(1.3f), icon = Duo.Check, height = 52.dp)
                    }
                    Stage.DONE -> {
                        if (!timed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            GlassIconButton(Duo.Remove, { reps = (reps - 1).coerceAtLeast(0) })
                            Text("$reps reps", style = FitType.title, color = th.text, modifier = Modifier.padding(horizontal = 18.dp))
                            GlassIconButton(Duo.Add, { reps += 1 })
                        } else Caption("Time: ${elapsed / 1000}s", Modifier.fillMaxWidth(), color = th.text)
                        weightText?.let { Caption("Weight: $it — change it on the workout screen if needed.", Modifier.fillMaxWidth()) }
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
                        GlassButton("+15 s", { container.restTimer.add(15, s.restSound, s.restVibrate); restLeft += 15 }, Modifier.weight(1f), height = 50.dp)
                        AccentButton("Skip rest", { vm.skipRest(); stage = Stage.INTRO }, Modifier.weight(1.3f), icon = Duo.ArrowForward, height = 50.dp)
                    }
                }
                Caption("Guidance only — stop if anything hurts. Camera frames stay on your phone.", Modifier.fillMaxWidth(), color = th.textFaint)
            }
        }
    }
}

/** Rep / time ring with the tempo phase word. */
@Composable
private fun BigCounter(stage: Stage, reps: Int, goal: Int, timed: Boolean, elapsed: Long, goalSec: Long, restLeft: Int, phaseIdx: Int, pattern: FormGuide.Pattern, phaseProg: Float, urdu: Boolean) {
    val th = LocalFitTheme.current
    val (big, small, frac) = when {
        stage == Stage.COUNTDOWN -> Triple("3·2·1", if (urdu) "تیار" else "Get ready", 0f)
        stage == Stage.REST -> Triple("${restLeft}s", if (urdu) "آرام" else "Rest", 0f)
        timed -> { val left = (goalSec - elapsed / 1000).coerceAtLeast(0); Triple("${left}s", if (urdu) "رکے رہیں" else "Hold", (elapsed / 1000f / goalSec).coerceIn(0f, 1f)) }
        else -> Triple("$reps", "/ $goal", reps / goal.toFloat())
    }
    val phase = FormGuide.tempo(pattern).getOrNull(phaseIdx)?.word?.text(urdu)
    Row(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = 6.dp.toPx()
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
