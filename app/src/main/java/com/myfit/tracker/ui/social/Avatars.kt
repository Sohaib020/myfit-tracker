package com.myfit.tracker.ui.social

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.social.MyAvatar
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.fadeEdges
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ready-made profile pictures bundled in assets/avatars (catalog.json lists them by category). */
object AvatarCatalog {
    val labels = linkedMapOf("characters" to "Our cast", "sports" to "Sports", "creatures" to "Creatures", "faces" to "Faces", "food" to "Food", "icons" to "Icons")
    @Volatile private var cache: Map<String, List<String>>? = null
    fun all(ctx: android.content.Context): Map<String, List<String>> = cache ?: runCatching {
        val o = org.json.JSONObject(ctx.assets.open("avatars/catalog.json").bufferedReader().readText())
        labels.keys.filter { o.has(it) }.associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } }
    }.getOrDefault(emptyMap()).also { cache = it }
    fun isPreset(id: String) = id.isNotBlank() && id != MyAvatar.PHOTO
}

private object AvatarBitmaps {
    val lru = object : android.util.LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
}

@Composable
private fun rememberPreset(id: String): ImageBitmap? {
    val ctx = LocalContext.current
    val b by produceState(AvatarBitmaps.lru.get(id), id) {
        if (value == null && AvatarCatalog.isPreset(id)) value = withContext(Dispatchers.IO) {
            runCatching { ctx.assets.open("avatars/$id.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        }?.also { AvatarBitmaps.lru.put(id, it) }
    }
    return b
}

@Composable
private fun rememberBase64(data: String?): ImageBitmap? {
    val key = data?.let { "b64:" + it.hashCode() + ":" + it.length }
    val b by produceState(key?.let { AvatarBitmaps.lru.get(it) }, key) {
        if (value == null && data != null && key != null) value = withContext(Dispatchers.Default) {
            runCatching { android.util.Base64.decode(data, android.util.Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) }.asImageBitmap() }.getOrNull()
        }?.also { AvatarBitmaps.lru.put(key, it) }
    }
    return b
}

/**
 * A person's round picture: their own photo (friends only), a ready-made avatar, or their initial on their colour.
 * [ring] draws a thin coloured outline (used for "you" and podium places).
 */
@Composable
fun UserAvatar(avatar: String?, photo: String?, name: String, color: Long, size: Dp, modifier: Modifier = Modifier, ring: Color? = null,
               gphoto: String? = null, seed: String = name) {
    // order: their own photo (friends only) → ready-made avatar → Google account photo → a unique gradient
    val preset = rememberPreset(avatar.orEmpty())
    val ph = if (avatar == MyAvatar.PHOTO) rememberBase64(photo) else null
    val g = if (ph == null && preset == null) rememberUrlPhoto(gphoto) else null
    val img = ph ?: preset ?: g
    Box(modifier.size(size).clip(CircleShape).then(if (ring != null) Modifier.border(size * 0.05f, ring, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else GradientAvatar(seed, Modifier.fillMaxSize())
    }
}

/**
 * Default picture for people who haven't chosen one: soft abstract shapes whose colours and layout come from
 * their id, so everyone gets a different one (and always the same one).
 */
@Composable
fun GradientAvatar(seed: String, modifier: Modifier = Modifier) {
    val h = remember(seed) { seed.fold(1125899906842597L) { a, c -> 31 * a + c.code } }
    val palettes = remember {
        listOf(
            listOf(0xFF7F5AF0, 0xFF2CB1BC, 0xFFFFD166), listOf(0xFFFF6B6B, 0xFFFFA45B, 0xFFFFE066), listOf(0xFF06D6A0, 0xFF118AB2, 0xFF073B4C),
            listOf(0xFFEF476F, 0xFF8338EC, 0xFF3A86FF), listOf(0xFF2EC4B6, 0xFFCBF3F0, 0xFFFF9F1C), listOf(0xFF4361EE, 0xFF4CC9F0, 0xFFF72585),
            listOf(0xFF52B788, 0xFFB7E4C7, 0xFF1B4332), listOf(0xFFF15BB5, 0xFFFEE440, 0xFF00BBF9), listOf(0xFFE76F51, 0xFFF4A261, 0xFF2A9D8F),
            listOf(0xFF6D597A, 0xFFB56576, 0xFFEAAC8B), listOf(0xFF3D5A80, 0xFF98C1D9, 0xFFEE6C4D), listOf(0xFF9B5DE5, 0xFF00F5D4, 0xFFFEE440),
        ).map { p -> p.map { Color(it) } }
    }
    val pal = palettes[((h ushr 3) % palettes.size).toInt().let { if (it < 0) -it else it } % palettes.size]
    fun r(k: Int): Float = (((h ushr (k * 5)) and 0xFF).toFloat() / 255f)
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val ht = size.height
        drawRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(pal[0], pal[1]),
            androidx.compose.ui.geometry.Offset(w * r(1), 0f), androidx.compose.ui.geometry.Offset(w * (1 - r(2)), ht)))
        // three overlapping blobs
        for (i in 0 until 3) {
            val c = pal[(i + 1) % 3].copy(alpha = 0.55f + 0.3f * r(i + 4))
            val cx = w * (0.15f + 0.7f * r(i + 7)); val cy = ht * (0.15f + 0.7f * r(i + 10))
            val rad = w * (0.22f + 0.25f * r(i + 13))
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), androidx.compose.ui.geometry.Offset(cx, cy), rad), rad,
                androidx.compose.ui.geometry.Offset(cx, cy))
        }
        // a soft light sweep for depth
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent, Color.Black.copy(alpha = 0.12f))))
    }
}

/** Downloads a small profile photo once (Google photo host only) and keeps it on the phone. */
@Composable
fun rememberUrlPhoto(url: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val ok = url != null && url.startsWith("https://lh") && ".googleusercontent.com/" in url
    val key = if (ok) "url:" + url.hashCode() else null
    val b by produceState(key?.let { AvatarBitmaps.lru.get(it) }, key) {
        if (value == null && key != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(ctx.cacheDir, "gphoto").apply { mkdirs() }
                val f = java.io.File(dir, key.substringAfter(':') + ".jpg")
                if (!f.exists() || f.length() < 100) {
                    val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 10000 }
                    try { if (c.responseCode in 200..299) c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } } } finally { c.disconnect() }
                }
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }?.also { AvatarBitmaps.lru.put(key, it) }
    }
    return b
}

/** Your own picture (works offline): photo file, ready-made avatar or initial. */
@Composable
fun MyAvatarImage(name: String, color: Long, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val ctx = LocalContext.current
    val v by MyAvatar.version.collectAsState()
    val id = remember(v) { MyAvatar.id(ctx) }
    val photo by produceState<ImageBitmap?>(null, v) {
        value = if (id == MyAvatar.PHOTO) withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(MyAvatar.photoFile(ctx).absolutePath)?.asImageBitmap() }.getOrNull() } else null
    }
    val preset = rememberPreset(id)
    val img = photo ?: preset
    Box(modifier.size(size).clip(CircleShape).then(if (ring != null) Modifier.border(size * 0.05f, ring, CircleShape) else Modifier).background(Color(color)), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, "Your picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val gp = rememberUrlPhoto(remember { runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() }.getOrNull() })
            if (gp != null) Image(gp, "Your picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else GradientAvatar(remember { runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: name }, Modifier.fillMaxSize())
        }
    }
}

/** The bitmap of your chosen picture (null = none chosen), for places that take an ImageBitmap (Me pill, maps). */
@Composable
fun rememberMyChosenAvatar(): ImageBitmap? {
    val ctx = LocalContext.current
    val v by MyAvatar.version.collectAsState()
    val id = remember(v) { MyAvatar.id(ctx) }
    val photo by produceState<ImageBitmap?>(null, v) {
        value = if (id == MyAvatar.PHOTO) withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(MyAvatar.photoFile(ctx).absolutePath)?.asImageBitmap() }.getOrNull() } else null
    }
    return photo ?: rememberPreset(id)
}

/** Game-Hub-style picker: big preview, your photo, then every ready-made picture by category. */
@Composable
fun AvatarPickerContent(container: AppContainer, name: String, color: Long, onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val cats = remember { AvatarCatalog.all(ctx) }
    var cat by remember { mutableStateOf(cats.keys.firstOrNull() ?: "characters") }
    var picked by remember { mutableStateOf(MyAvatar.id(ctx)) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (MyAvatar.setPhoto(ctx, uri)) {
                picked = MyAvatar.PHOTO
                runCatching { container.social.uploadAvatar() }; runCatching { container.friendsRepo.refresh(0) }
                toaster.show("Photo set — friends see it after the next sync"); onDone()
            } else toaster.show("Couldn't open that photo")
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Profile picture", style = FitType.title, color = th.text)
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.BottomEnd) {
            if (picked == MyAvatar.PHOTO || picked == MyAvatar.id(ctx)) MyAvatarImage(name, color, 112.dp, ring = th.accentBright)
            else UserAvatar(picked, null, name, color, 112.dp, ring = th.accentBright)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GlassButton("Use a photo", { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), icon = Duo.Images, height = 44.dp)
            GlassButton("Remove", {
                MyAvatar.clear(ctx); picked = ""
                scope.launch { runCatching { container.social.uploadAvatar() } }
            }, Modifier.weight(1f), icon = Duo.Close, height = 44.dp)
        }
        Spacer(Modifier.height(4.dp))
        Caption("Your photo is shrunk to a small circle and only your friends can see it.", color = th.textFaint)
        Spacer(Modifier.height(12.dp))
        val chips = rememberLazyListState()
        LazyRow(state = chips, modifier = Modifier.fillMaxWidth().fadeEdges(chips), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.keys.forEach { k -> item(k) { GlassChip(AvatarCatalog.labels[k] ?: k, cat == k, { cat = k }) } }
        }
        Spacer(Modifier.height(12.dp))
        val ids = cats[cat].orEmpty()
        LazyVerticalGrid(GridCells.Adaptive(62.dp), Modifier.fillMaxWidth().heightIn(max = 340.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ids, key = { it }) { id ->
                val sel = picked == id
                Box(Modifier.aspectRatio(1f).clip(CircleShape).then(if (sel) Modifier.border(3.dp, th.accentBright, CircleShape) else Modifier).clickableNoRipple { picked = id }.padding(if (sel) 4.dp else 0.dp)) {
                    UserAvatar(id, null, name, color, 62.dp, Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        AccentButton("Save", {
            if (AvatarCatalog.isPreset(picked)) MyAvatar.setPreset(ctx, picked)
            scope.launch { runCatching { container.social.uploadAvatar() }; runCatching { container.friendsRepo.refresh(0) } }
            onDone()
        }, Modifier.fillMaxWidth(), icon = Duo.Check)
    }
}
