package com.myfit.tracker.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Signed-in account's profile photo, downloaded once and cached in app storage (null if none / offline). */
@Composable
fun rememberAccountPhoto(container: AppContainer): ImageBitmap? {
    val ctx = LocalContext.current
    val user by container.social.user.collectAsState()
    val url = user?.photoUrl?.toString()
    val uid = user?.uid
    val img by produceState<ImageBitmap?>(null, url, uid) {
        value = if (url == null || uid == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val f = File(ctx.filesDir, "avatar_${uid.take(12)}.jpg")
                if (!f.exists() || f.length() < 100) {
                    val big = url.replace(Regex("=s\\d+-c$"), "=s256-c")   // Google photos: ask for 256 px
                    val c = (URL(big).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
                    try { if (c.responseCode in 200..299) c.inputStream.use { inp -> f.outputStream().use { inp.copyTo(it) } } } finally { c.disconnect() }
                }
                android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}

/** Round avatar: account photo, or [fallback] (usually the initial). */
@Composable
fun AccountAvatar(photo: ImageBitmap?, size: androidx.compose.ui.unit.Dp, fallback: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else fallback()
    }
}

/** Me → online account: photo, name, email, how you signed in, sign out. */
@Composable
fun AccountCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val social = container.social
    val user by social.user.collectAsState()
    val photo = rememberAccountPhoto(container)
    GlassCard {
        CardHeader(Duo.Cloud, "Online account", th.water)
        Spacer(Modifier.height(12.dp))
        val u = user
        if (!social.available) { Caption("Online features aren't switched on in this build."); return@GlassCard }
        if (u == null) { Caption("Not signed in."); return@GlassCard }
        val google = u.providerData.any { it.providerId == "google.com" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(photo, 56.dp) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text((u.displayName ?: u.email ?: "?").take(1).uppercase(), style = FitType.title, color = th.text)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(u.displayName?.takeIf { it.isNotBlank() } ?: "MyFit user", style = FitType.section, color = th.text)
                u.email?.let { Caption(it) }
                Caption(if (google) "Signed in with Google" else "Signed in with email", color = th.accentBright)
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassButton("Sign out", { social.signOut() }, Modifier.fillMaxWidth(), height = 44.dp)
        Spacer(Modifier.height(6.dp))
        Caption("Signing out keeps your logs on this phone; you'll be asked to sign in again.", color = th.textFaint)
    }
}
