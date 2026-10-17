package com.myfit.tracker.ui.game

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.coach.Champ3DController
import com.myfit.tracker.ui.coach.Champ3DLayer
import com.myfit.tracker.ui.coach.ChampModels
import com.myfit.tracker.ui.coach.Coach3D
import com.myfit.tracker.ui.coach.CoachClip
import com.myfit.tracker.ui.pip.Buddy
import com.myfit.tracker.ui.pip.BuddyAvatar
import com.myfit.tracker.ui.pip.BuddyChar
import java.io.File

/** Moves that loop; everything else plays once and calls onEnd. */
val CHAMP_LOOPS = setOf("idle", "talk", "dance", "train", "sad", "stretch", "jog", "jumpingjacks")

/**
 * A champion look on screen: live 3D on capable phones (drag to spin), the clip pack elsewhere, the portrait while
 * either downloads. [pose] is a move name (idle, celebrate, combo, …); unknown names fall back like the buddy does.
 */
@Composable
fun ChampStage(b: BuddyChar, pose: String, modifier: Modifier = Modifier, onEnd: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val tick by Coach3D.ready.collectAsState()
    val dl by Buddy.downloading.collectAsState()
    val threeD = remember { Coach3D.enabled(ctx) }
    var failed3d by remember(b.id) { mutableStateOf(false) }
    var packTick by remember(b.id) { mutableStateOf(0) }
    val use3d = threeD && !failed3d && remember(b.id, tick) { ChampModels.installed(ctx, b.id) }
    // fetch what this phone uses (3D model or clip pack), never both
    LaunchedEffect(b.id, threeD, failed3d) {
        if (threeD && !failed3d) ChampModels.ensure(ctx, b.id)
        else if (!Buddy.installed(ctx, b)) { Buddy.download(ctx, b); packTick++ }
    }
    val end by rememberUpdatedState(onEnd)
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (use3d) {
            val ctl = remember(b.id) { Champ3DController() }
            LaunchedEffect(ctl, pose) { ctl.play(pose, pose in CHAMP_LOOPS) { end?.invoke() } }
            Champ3DLayer(b.id, b.tier.ordinal, ctl, Modifier.fillMaxSize(), onFail = { failed3d = true })
        } else {
            val file = remember(b.id, pose, packTick, dl) { Buddy.champClip(ctx, b, pose) }
            if (file != null && android.os.Build.VERSION.SDK_INT >= 28) {
                CoachClip(file, Modifier.fillMaxSize(), loop = pose in CHAMP_LOOPS, onEnd = { end?.invoke() })
            } else {
                // still downloading (or Android 8): portrait + a quiet progress ring; one-shot poses end right away
                LaunchedEffect(pose) { if (pose !in CHAMP_LOOPS) { kotlinx.coroutines.delay(1500); end?.invoke() } }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BuddyAvatar(b, 120.dp)
                    if (b.id in dl || (threeD && !use3d && !failed3d)) CircularProgressIndicator(Modifier.size(136.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

private val stillCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(40)

/**
 * R26: a champion look as a full-body 3D still (bundled champ/<id>/stand.webp, rendered from the model) — the glimpse
 * of a look you haven't unlocked yet. No animation and nothing downloads until it's yours.
 */
@Composable
fun ChampStill(b: BuddyChar, modifier: Modifier = Modifier, locked: Boolean = false, full: Boolean = false) {
    val ctx = LocalContext.current
    val path = Buddy.standAsset(b)
    val key = "$path@$full"
    val bmp by androidx.compose.runtime.produceState(stillCache.get(key), key) {
        value = stillCache.get(key) ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // thumbnails decode at half size (360 px) so a roster of stills stays light on memory
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = if (full) 1 else 2 }
            (runCatching { ctx.assets.open(path) }.getOrNull() ?: runCatching { ctx.assets.open(Buddy.portraitAsset(b)) }.getOrNull())
                ?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
        }?.also { stillCache.put(key, it) }
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        bmp?.let {
            androidx.compose.foundation.Image(it, b.label, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                // R28: locked looks show in full colour — the lock chip on the card says it's locked
                colorFilter = null, alpha = 1f)
        }
    }
}
