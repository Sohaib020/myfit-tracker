package com.myfit.tracker.ui.food

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.FoodVision
import com.myfit.tracker.data.db.NutritionSource
import com.myfit.tracker.data.repo.LogLine
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** One detected item, editable before saving. Nutrition scales with grams. */
private class PhotoItem(val src: FoodVision.Item) {
    var on by mutableStateOf(true)
    var name by mutableStateOf(src.name)
    var grams by mutableStateOf(if (src.grams > 0) Fmt.trim(src.grams, 0) else "")
    /** Unchanged since it arrived → may be replaced by the accuracy pass. */
    val untouched get() = on && name == src.name && grams == (if (src.grams > 0) Fmt.trim(src.grams, 0) else "")
    val factor: Double get() = if (src.grams > 0) (grams.toDoubleOrNull() ?: 0.0) / src.grams else 1.0
    val kcal get() = src.kcal * factor
    val p get() = src.protein * factor
    val c get() = src.carbs * factor
    val f get() = src.fat * factor
}

private sealed interface Stage {
    data object Pick : Stage
    data object Working : Stage
    data class Done(val note: String?) : Stage
    data class Failed(val msg: String, val notFood: Boolean) : Stage
}

/** Snap or pick a meal photo → Gemini estimates items + nutrition → user edits → logged as AI_PHOTO estimates. */
@Composable
fun FoodPhotoScreen(container: AppContainer, mealType0: String, dateKey: String) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val date = LocalDate.parse(dateKey)
    var mealType by remember { mutableStateOf(mealType0) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var stage by remember { mutableStateOf<Stage>(Stage.Pick) }
    var hint by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<PhotoItem>() }
    var refining by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var tookMs by remember { mutableStateOf<Long?>(null) }

    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun analyse() {
        val b = photo ?: return
        job?.cancel()
        stage = Stage.Working
        items.clear()
        refining = false; tookMs = null; startedAt = System.currentTimeMillis()
        job = scope.launch {
            stage = try {
                val r = FoodVision(container).analyze(b, hint) { early ->
                    // quick pass: show it now, keep improving in the background
                    items.clear(); items.addAll(early.items.map { PhotoItem(it) })
                    tookMs = System.currentTimeMillis() - startedAt
                    refining = true
                    stage = Stage.Done(early.note)
                }
                refining = false
                if (tookMs == null) tookMs = System.currentTimeMillis() - startedAt
                if (items.isEmpty() || items.all { it.untouched }) { items.clear(); items.addAll(r.items.map { PhotoItem(it) }) }
                Stage.Done(r.note)
            } catch (e: FoodVision.NotFood) {
                Stage.Failed(e.message ?: "No food found", true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                refining = false
                if (items.isNotEmpty()) Stage.Done(null) else Stage.Failed(e.message ?: "Something went wrong", false)
            }
        }
    }

    fun load(uri: Uri) {
        scope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { decode(ctx, uri) }.getOrNull() }
            if (b == null) { toaster.show("Couldn't open that photo"); return@launch }
            photo = b
            analyse()
        }
    }

    var showCamera by remember { mutableStateOf(true) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { showCamera = false; load(uri) } }
    var hasCam by remember { mutableStateOf(com.myfit.tracker.ui.onboarding.hasPerm(ctx, android.Manifest.permission.CAMERA)) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCam = ok
        if (ok) showCamera = true
        else { showCamera = false; toaster.show("Camera not allowed — you can pick a photo from the gallery") }
    }
    fun snap() { if (hasCam) showCamera = true else camPerm.launch(android.Manifest.permission.CAMERA) }
    fun pick() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    LaunchedEffect(Unit) { if (!hasCam) camPerm.launch(android.Manifest.permission.CAMERA) }

    if (showCamera && hasCam) {
        androidx.activity.compose.BackHandler { if (photo == null) nav.pop() else showCamera = false }
        FoodCamera(
            container, "${mealLabel(mealType)} · ${if (date == Clock.today()) "today" else dateKey}",
            onCaptured = { b -> photo = b; showCamera = false; analyse() },
            onGallery = { pick() },
            onClose = { if (photo == null) nav.pop() else showCamera = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Snap a meal", { nav.pop() }, "${mealLabel(mealType)} · ${if (date == Clock.today()) "today" else dateKey}")
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MEAL_ORDER.dropLast(1)) { t -> GlassChip(mealLabel(t), t == mealType, { mealType = t }) }
                }
            }
            item {
                val b = photo
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    if (b != null) {
                        Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBubble(Duo.Camera, th.accent, 56.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Photograph your plate", style = FitType.section, color = th.text)
                            Caption("Shoot from above in good light, with the whole plate in frame. A spoon or hand in the shot helps judge portions.", Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(if (photo == null) "Camera" else "Retake", { snap() }, Modifier.weight(1f), icon = Duo.Camera, height = 46.dp)
                    GlassButton("Gallery", { pick() }, Modifier.weight(1f), icon = Duo.Images, height = 46.dp)
                }
            }
            when (val s = stage) {
                Stage.Pick -> item {
                    Caption("The photo is sent to an online AI (Gemini, or a backup service if it is busy) for analysis. Results are estimates — check portions before saving.", color = th.textFaint)
                }
                Stage.Working -> item {
                    Glass(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pip(PipMood.THINKING, size = 64.dp, interactive = false, idleActions = false)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Pip is looking at your plate…", style = FitType.section, color = th.text)
                                Caption("Identifying dishes and estimating portions")
                            }
                        }
                    }
                }
                is Stage.Failed -> item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pip(PipMood.CONCERNED, size = 56.dp, interactive = false, idleActions = false)
                                Spacer(Modifier.width(10.dp))
                                Text(s.msg, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            if (!s.notFood && photo != null) GlassButton("Try again", { analyse() }, Modifier.fillMaxWidth(), height = 44.dp)
                            Spacer(Modifier.height(6.dp))
                            GlassButton("Search foods instead", { nav.replace(Overlay.FoodAdd(mealType, dateKey, 0)) }, Modifier.fillMaxWidth(), icon = Duo.ForkKnife, height = 44.dp)
                        }
                    }
                }
                is Stage.Done -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FOUND ${items.size} ITEM${if (items.size == 1) "" else "S"}" + (tookMs?.let { " · ${"%.1f".format(it / 1000.0)} s" } ?: ""),
                                style = FitType.overline, color = th.textDim, modifier = Modifier.weight(1f))
                            if (refining) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = th.accent, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Caption("Refining…", color = th.accentBright)
                            } else EstimateTag()
                        }
                    }
                    itemsIndexed(items, key = { i, _ -> i }) { _, it -> PhotoItemRow(it) }
                    s.note?.let { n -> item { Caption("Pip: $n", color = th.textDim) } }
                    container.aiRouter.lastProvider?.let { pv -> item { Caption("Analysed by $pv", color = th.textFaint) } }
                    item {
                        Glass(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                val sel = items.filter { it.on }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Total", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                                    Text("≈ ${Fmt.int(sel.sumOf { it.kcal })} ${com.myfit.tracker.domain.EnergyUnit.label}", style = FitType.title, color = th.text)
                                }
                                Caption("P ${Fmt.int(sel.sumOf { it.p })} g · C ${Fmt.int(sel.sumOf { it.c })} g · F ${Fmt.int(sel.sumOf { it.f })} g")
                            }
                        }
                    }
                    item {
                        Caption("Wrong dish? Add a note and re-check:", Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(6.dp))
                        com.myfit.tracker.ui.entries.NotesField(hint, { hint = it.take(120) }, "e.g. it's daal mash, half plate, cooked in less oil")
                        Spacer(Modifier.height(6.dp))
                        GlassButton("Re-check with note", { analyse() }, Modifier.fillMaxWidth(), height = 44.dp)
                    }
                }
            }
        }
        if (stage is Stage.Done) {
            val sel = items.filter { it.on && it.name.isNotBlank() }
            AccentButton(
                "Log ${sel.size} item${if (sel.size == 1) "" else "s"} to ${mealLabel(mealType)}",
                {
                    if (sel.isEmpty()) { toaster.show("Select at least one item"); return@AccentButton }
                    val lines = sel.map { pi ->
                        val g = pi.grams.toDoubleOrNull()
                        val byGrams = pi.src.grams > 0 && g != null && g > 0
                        LogLine(
                            name = pi.name.trim(), quantity = 1.0,
                            servingSize = if (byGrams) g!! else 1.0, servingUnit = if (byGrams) "g" else "serving",
                            kcal = pi.kcal, protein = pi.p, carbs = pi.c, fat = pi.f, fiber = pi.src.fiber?.let { it * pi.factor },
                            source = NutritionSource.AI_PHOTO,
                        )
                    }
                    container.write { container.nutritionRepo.log(date, mealType, lines) }
                    toaster.show("Logged ≈ ${Fmt.int(sel.sumOf { it.kcal })} ${com.myfit.tracker.domain.EnergyUnit.label}")
                    nav.pop()
                },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp),
                icon = Duo.Check,
            )
        }
    }
}

@Composable
private fun PhotoItemRow(it: PhotoItem) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).clickableNoRipple { it.on = !it.on },
                    contentAlignment = Alignment.Center,
                ) {
                    if (it.on) IconBubble(Duo.Check, th.accent, 28.dp)
                    else Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).then(Modifier)) { Icon(Duo.Check, null, tint = th.textFaint, modifier = Modifier.size(16.dp).align(Alignment.Center)) }
                }
                Spacer(Modifier.width(10.dp))
                FoodThumb(it.src.photo, 44.dp, 12.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.name, style = FitType.section, color = if (it.on) th.text else th.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Caption(listOf(it.src.portion, if (it.src.catalogUuid != null) "MyFit values" else "${it.src.confidence} confidence").filter { s -> s.isNotBlank() }.joinToString(" · "))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("≈ ${Fmt.int(it.kcal)}", style = FitType.section, color = th.text, textAlign = TextAlign.End)
                    Caption("${com.myfit.tracker.domain.EnergyUnit.label}")
                }
            }
            if (it.on) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (it.src.grams > 0) NumberInput(it.grams, { v -> it.grams = v }, "g", Modifier.width(130.dp), decimal = false, big = false)
                    Spacer(Modifier.width(10.dp))
                    Caption("P ${Fmt.int(it.p)} · C ${Fmt.int(it.c)} · F ${Fmt.int(it.f)} g", Modifier.weight(1f))
                }
            }
        }
    }
}


/** Decodes at a sensible size (≤ ~1600 px) with orientation applied. */
private fun decode(ctx: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val m = maxOf(info.size.width, info.size.height)
            if (m > 1600) { val s = 1600f / m; d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt()) }
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1200) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
    val deg = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use {
            when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90; android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
            }
        }
    }.getOrDefault(0)
    return if (deg == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg.toFloat()) }, true)
}
