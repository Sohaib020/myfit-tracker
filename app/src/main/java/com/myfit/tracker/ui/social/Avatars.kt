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
fun UserAvatar(avatar: String?, photo: String?, name: String, color: Long, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val preset = rememberPreset(avatar.orEmpty())
    val ph = if (avatar == MyAvatar.PHOTO) rememberBase64(photo) else null
    val img = ph ?: preset
    Box(modifier.size(size).clip(CircleShape).then(if (ring != null) Modifier.border(size * 0.05f, ring, CircleShape) else Modifier).background(Color(color)), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase().ifBlank { "?" }, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
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
        else Text(name.trim().take(1).uppercase().ifBlank { "?" }, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, color = Color.White)
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
