package com.myfit.tracker.ui.social

import com.myfit.tracker.ui.components.fadeEdges
import androidx.compose.foundation.horizontalScroll

import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.social.BoardRow
import com.myfit.tracker.social.Challenge
import com.myfit.tracker.social.ChallengeRow
import com.myfit.tracker.social.Metric
import com.myfit.tracker.social.Profile
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

fun formatMetric(m: Metric, v: Double): String = when (m) {
    Metric.STEPS -> Fmt.int(v)
    Metric.ACTIVE -> "${Fmt.int(v)} min"
    Metric.DISTANCE -> Fmt.trim(v / 1000.0, 1) + " km"
}

/** Friends, challenges and leaderboards. */
@Composable
fun SocialScreen(container: AppContainer, asTab: Boolean = false, bottomPad: Int = 40, embedded: Boolean = false) {
    val nav = LocalNav.current
    val social = container.social
    val user by social.user.collectAsState()
    val sub = if (user != null) "Leaderboards, challenges & friends · only device-recorded activity counts" else "Sign in to challenge friends"
    if (social.available && user != null && !asTab) { FriendsHome(container, bottomPad, showTopBar = !embedded); return }
    Column(Modifier.fillMaxSize()) {
        if (embedded) Unit
        else if (asTab) Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = com.myfit.tracker.ui.components.TopBarSpace, bottom = 6.dp)) {
            Text("Arena", style = com.myfit.tracker.ui.theme.FitType.display, color = LocalFitTheme.current.text)
            Caption(sub)
        } else OverlayTopBar("Friends", { nav.pop() }, if (user != null) "Compete, add friends and see what they're up to" else "Sign in to compete with friends")
        when {
            !social.available -> NotConfigured()
            user == null -> SignIn(container, bottomPad)
            else -> FriendsHome(container, bottomPad, showTopBar = false)
        }
    }
}

@Composable
private fun NotConfigured() {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        GlassCard {
            CardHeader(Duo.Cloud, "Online features aren't switched on yet", LocalFitTheme.current.warning)
            Spacer(Modifier.height(8.dp))
            Caption("This build was made without the Firebase connection. Once the GOOGLE_SERVICES_JSON secret is added to the GitHub repository, the next build lets you sign in, add friends and compete.")
        }
    }
}

// ------------------------------------------------------------------ sign in

@Composable
internal fun SignIn(container: AppContainer, bottomPad: Int = 40, gate: Boolean = false) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val social = container.social
    var mode by remember { mutableIntStateOf(0) }   // 0 sign in, 1 create
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun google() {
        val webId = social.webClientId() ?: run { error = "Google sign-in isn't set up in Firebase yet (enable it, then re-download google-services.json)."; return }
        busy = true; error = null
        scope.launch {
            try {
                val req = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(webId).build()).build()
                val cred = CredentialManager.create(ctx).getCredential(ctx, req).credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    social.signInWithGoogleToken(GoogleIdTokenCredential.createFrom(cred.data).idToken)
                    runCatching { social.uploadNow(5) }
                    toaster.show("Signed in")
                } else error = "That account type isn't supported."
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                // user closed the picker
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                error = friendly(e)
            }
            busy = false
        }
    }

    fun emailGo() {
        if (email.isBlank() || pass.length < 6 || (mode == 1 && name.isBlank())) { error = "Enter your email, a password of 6+ characters" + if (mode == 1) " and a display name." else "."; return }
        busy = true; error = null
        scope.launch {
            try {
                if (mode == 0) social.signInEmail(email, pass) else social.createEmail(email, pass, name)
                runCatching { social.uploadNow(5) }
                toaster.show(if (mode == 0) "Signed in" else "Account created")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { error = friendly(e) }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPad.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Glass(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pip(if (gate) PipMood.WAVE else PipMood.LETS_GO, size = 84.dp, interactive = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (gate) "Create your MyFit account" else "Challenge your friends", style = FitType.section, color = th.text)
                        Caption(if (gate) "One account for leaderboards, challenges and friends. Your health logs still stay on this phone."
                            else "Weekly leaderboards for steps, distance and watch-recorded workouts. Hand-typed numbers never count, so it's fair.")
                    }
                }
            }
        }
        item {
            AccentButton(if (busy) "Please wait…" else "Continue with Google", { if (!busy) google() }, Modifier.fillMaxWidth(), icon = Duo.Person)
        }
        item { Text("OR USE EMAIL", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 6.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Sign in", mode == 0, { mode = 0 }); GlassChip("Create account", mode == 1, { mode = 1 })
            }
        }
        if (mode == 1) item { Field(name, { name = it.take(24) }, "Display name (shown to friends)") }
        item { Field(email, { email = it.trim() }, "Email", KeyboardType.Email) }
        item { Field(pass, { pass = it }, "Password (6+ characters)", KeyboardType.Password, secret = true) }
        error?.let { e -> item { Caption(e, color = th.danger) } }
        item { GlassButton(if (mode == 0) "Sign in" else "Create account", { if (!busy) emailGo() }, Modifier.fillMaxWidth(), height = 50.dp) }
        if (mode == 0) item {
            Text("Forgot password?", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple {
                if (email.isBlank()) { error = "Type your email first."; return@clickableNoRipple }
                scope.launch { runCatching { social.resetPassword(email) }.onSuccess { toaster.show("Reset link sent to $email") }.onFailure { error = friendly(it) } }
            }.padding(6.dp))
        }
        item { Caption("Your MyFit logs stay on this phone. Online, friends see only your display name and weekly totals of steps, distance and workout minutes.", color = th.textFaint) }
    }
}

private fun friendly(e: Throwable): String {
    val m = e.message ?: ""
    return when {
        "password is invalid" in m || "INVALID_LOGIN_CREDENTIALS" in m || "credential is incorrect" in m -> "Email or password is wrong."
        "no user record" in m -> "No account with that email — choose Create account."
        "already in use" in m -> "That email already has an account — choose Sign in."
        "badly formatted" in m -> "That email doesn't look right."
        "network" in m.lowercase() -> "No internet connection."
        "No credentials available" in m -> "No Google account found on this phone."
        else -> m.take(140).ifBlank { "Something went wrong." }
    }
}

@Composable
internal fun Field(v: String, on: (String) -> Unit, hint: String, kb: KeyboardType = KeyboardType.Text, secret: Boolean = false) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (v.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint)
            BasicTextField(
                v, on, singleLine = true, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = kb),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun Avatar(name: String, color: Long, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifBlank { "?" }, style = FitType.section, color = Color.White)
    }
}


/** Full-screen sign-in shown before setup: every MyFit user has an online account. */
@Composable
fun SignInGate(container: AppContainer) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            Text("Welcome to MyFit", style = FitType.display, color = th.text)
            Caption("Sign in to get started — it takes one tap with Google.")
        }
        SignIn(container, 40, gate = true)
    }
}


/** Your invite: QR code + link that works even for people who don't have the app (download page + "Open in MyFit"). */
@Composable
internal fun InviteCard(profile: Profile?) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val code = profile?.code
    val link = code?.let { com.myfit.tracker.social.Invite.link(it, profile.name) }
    val qr = remember(link) { link?.let { runCatching { com.myfit.tracker.social.Invite.qr(it).asImageBitmap() }.getOrNull() } }
    GlassCard {
        CardHeader(Duo.Link, "Invite friends", th.accentBright)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(8.dp), contentAlignment = Alignment.Center) {
                if (qr != null) androidx.compose.foundation.Image(qr, "Invite QR code", Modifier.fillMaxSize()) else Caption("…")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Caption("YOUR MYFIT ID")
                Text(code ?: "······", style = FitType.display, color = th.text)
                Caption("Friends scan this QR or open your link. If they don't have MyFit yet, the page gives them the latest download.")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton("Share invite", { if (code != null) com.myfit.tracker.social.Invite.share(ctx, code, profile.name) }, Modifier.weight(1f), icon = Duo.Send, height = 44.dp)
            GlassButton("Copy link", {
                if (link != null) {
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("MyFit invite", link))
                    toaster.show("Invite link copied")
                }
            }, Modifier.weight(1f), icon = Duo.ContentCopy, height = 44.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Share the app link", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Get MyFit Tracker: ${com.myfit.tracker.update.Store.appLink}"), "Share download link"))
        }.padding(vertical = 6.dp))
    }
}


/** What friends have been up to: step milestones, finished workouts, level-ups. */
@Composable
internal fun ActivityFeed(container: AppContainer, refresh: Int) {
    val th = LocalFitTheme.current
    val items by produceState<List<com.myfit.tracker.social.Social.FeedItem>?>(null, refresh) {
        value = runCatching { container.social.feed() }.getOrDefault(emptyList())
    }
    val list = items
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            list == null -> Caption("Loading…")
            list.isEmpty() -> GlassCard {
                Text("Nothing yet", style = FitType.section, color = th.text)
                Caption("Milestones show up here — 10,000-step days, finished workouts and Arena level-ups from you and your friends.")
            }
            else -> list.forEach { f ->
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(f.name, 0xFF4C8DFFL, 38)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (f.me) "You " else f.name + " ") + f.text, style = FitType.body, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Caption(feedAgo(f.at))
                        }
                        androidx.compose.material3.Icon(when (f.kind) { "workout" -> Duo.FitnessCenter; "level" -> Duo.EmojiEvents; else -> Duo.DirectionsWalk }, null,
                            tint = th.accentBright, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

internal fun feedAgo(t: Long): String {
    if (t <= 0) return "just now"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when { m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
}
