package com.myfit.tracker.ui.social

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.social.BoardRow
import com.myfit.tracker.social.Challenge
import com.myfit.tracker.social.ChallengeRow
import com.myfit.tracker.social.FriendCard
import com.myfit.tracker.social.Metric
import com.myfit.tracker.social.Profile
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.fadeEdges
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

private val GOLD = Color(0xFFFFC83D)
private val SILVER = Color(0xFFC9D1DB)
private val BRONZE = Color(0xFFE09B5A)

private enum class Sheet { NONE, ADD, AVATAR, SETTINGS, PROFILE, NEW_CHALLENGE, CHALLENGE, GLOBAL }

/**
 * The Friends page: one clear scroll — you, this week's race (podium + everyone), challenges and recent activity.
 * Opens instantly from the on-phone cache and refreshes in the background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendsHome(container: AppContainer, bottomPad: Int = 40, showTopBar: Boolean = true) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val social = container.social
    val repo = container.friendsRepo
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { repo.load() }
    val snap by repo.snap.collectAsState()
    val refreshing by repo.refreshing.collectAsState()
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var openFriend by remember { mutableStateOf<FriendCard?>(null) }
    var openChallenge by remember { mutableStateOf<Challenge?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    var challenges by remember { mutableStateOf<List<Challenge>?>(null) }

    LaunchedEffect(bump) {
        runCatching { social.uploadNow(if (bump == 0) 5 else 0) }
        runCatching { repo.refresh(if (bump == 0) 20 else 0) }
        profile = runCatching { social.ensureProfile() }.getOrNull()
        challenges = runCatching { social.myChallenges() }.getOrDefault(emptyList())
    }
    fun refresh() { bump++ }

    val ranked = snap?.ranked.orEmpty()
    val me = snap?.me
    val myName = me?.name ?: profile?.name ?: social.user.value?.displayName ?: "You"
    val myColor = me?.color ?: profile?.color ?: 0xFFFF7A1AL
    val leader = ranked.maxOfOrNull { it.weekSteps }?.coerceAtLeast(1L) ?: 1L
    val myRank = ranked.indexOfFirst { it.me } + 1

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showTopBar) OverlayTopBar("Friends", { nav.pop() }) {
                GlassIconButton(Duo.Gear, { sheet = Sheet.SETTINGS })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPad.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---- you
                item("me") {
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(th.accentBright.copy(alpha = 0.18f), Color.Transparent))))
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.clickableNoRipple { sheet = Sheet.AVATAR }) {
                                    MyAvatarImage(myName, myColor, 68.dp, ring = th.accentBright)
                                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(24.dp).clip(CircleShape).background(th.accentBright), contentAlignment = Alignment.Center) {
                                        Icon(Duo.Camera, "Change picture", tint = Color(0xFF14161B), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(myName, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        me?.level?.let { LevelChip(it) }
                                    }
                                    val handle = profile?.username?.takeIf { it.isNotBlank() } ?: me?.username?.takeIf { it.isNotBlank() }
                                    Caption(if (handle != null) "@$handle" else "Tap ⚙ to pick a username")
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MeStat(if (myRank > 0 && ranked.size > 1) "#$myRank" else "—", "Rank", Modifier.weight(1f))
                                MeStat(Fmt.int((me?.weekSteps ?: 0L).toDouble()), "Steps", Modifier.weight(1f))
                                MeStat("${snap?.friends?.size ?: 0}", "Friends", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccentButton("Add friends", { sheet = Sheet.ADD }, Modifier.weight(1f), icon = Duo.Add, height = 46.dp)
                                GlassButton("Challenge", { sheet = Sheet.NEW_CHALLENGE }, Modifier.weight(1f), icon = Duo.Flag, height = 46.dp)
                            }
                        }
                    }
                }
                // ---- requests
                val reqs = snap?.requests ?: 0
                if (reqs > 0) item("req") {
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = { sheet = Sheet.ADD }) {
                        Row(Modifier.background(th.accent.copy(alpha = 0.14f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(th.accent), contentAlignment = Alignment.Center) { Text("$reqs", style = FitType.section, color = Color.White) }
                            Spacer(Modifier.width(12.dp))
                            Text(if (reqs == 1) "1 friend request" else "$reqs friend requests", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                            Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
                        }
                    }
                }
                // ---- this week
                item("weekhdr") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("This week", style = FitType.section, color = th.text)
                            Caption(when {
                                refreshing -> "Updating…"
                                snap == null -> "Loading…"
                                else -> "Steps since Monday · updated ${ago(snap!!.fetchedAt)}"
                            })
                        }
                        val spin by animateFloatAsState(if (refreshing) 360f else 0f, tween(if (refreshing) 900 else 0), label = "spin")
                        GlassIconButton(Duo.Sync, { refresh() }, modifier = Modifier.graphicsRotate(spin))
                    }
                }
                if (snap != null && snap!!.friends.isEmpty()) item("empty") {
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Pip(PipMood.WAVE, size = 96.dp, interactive = false)
                            Spacer(Modifier.height(8.dp))
                            Text("Bring a friend along", style = FitType.section, color = th.text)
                            Caption("Share your invite or add them by their MyFit ID. You'll both appear here and race on steps every week.", Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccentButton("Invite", { FriendActions.invite(container, ctx, scope) { toaster.show(it) } }, Modifier.weight(1f), icon = Duo.Send, height = 44.dp)
                                GlassButton("Scan QR", { FriendActions.scan(container, ctx, scope, { toaster.show(it) }) { refresh() } }, Modifier.weight(1f), icon = Duo.Scan, height = 44.dp)
                            }
                        }
                    }
                }
                if (ranked.size >= 2) item("podium") { Podium(ranked.take(3)) { if (!it.me) { openFriend = it; sheet = Sheet.PROFILE } } }
                if (ranked.size >= 2) items(ranked, key = { "r" + it.uid }) { f ->
                    val rank = ranked.indexOf(f) + 1
                    RaceRow(rank, f, f.weekSteps.toFloat() / leader) { if (!f.me) { openFriend = f; sheet = Sheet.PROFILE } else sheet = Sheet.AVATAR }
                }
                // ---- challenges
                val cs = challenges.orEmpty().filter { !runCatching { java.time.LocalDate.parse(it.end).isBefore(com.myfit.tracker.domain.Clock.today().minusDays(3)) }.getOrDefault(false) }
                if (cs.isNotEmpty() || (snap?.friends?.isNotEmpty() == true)) item("chhdr") {
                    Text("Challenges", style = FitType.section, color = th.text, modifier = Modifier.padding(top = 6.dp))
                }
                if (cs.isNotEmpty() || (snap?.friends?.isNotEmpty() == true)) item("ch") {
                    val st = rememberLazyListState()
                    LazyRow(state = st, modifier = Modifier.fillMaxWidth().fadeEdges(st), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(cs, key = { it.id }) { ch -> ChallengeCard(ch) { openChallenge = ch; sheet = Sheet.CHALLENGE } }
                        item("new") {
                            Glass(Modifier.width(150.dp).height(120.dp), shape = RoundedCornerShape(22.dp), onClick = { sheet = Sheet.NEW_CHALLENGE }) {
                                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(40.dp).clip(CircleShape).background(th.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(Duo.Add, null, tint = th.accentBright) }
                                    Spacer(Modifier.height(8.dp))
                                    Text("New challenge", style = FitType.label, color = th.text, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                // ---- activity + global
                if (snap?.friends?.isNotEmpty() == true) item("acthdr") { Text("Recent activity", style = FitType.section, color = th.text, modifier = Modifier.padding(top = 6.dp)) }
                if (snap?.friends?.isNotEmpty() == true) item("act") { ActivityStrip(container, ranked, bump) }
                item("global") {
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = { sheet = Sheet.GLOBAL }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Duo.Cloud, null, tint = th.water, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Global leaderboard", style = FitType.label, color = th.text)
                                Caption("Everyone who chose to appear publicly")
                            }
                            Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
                        }
                    }
                }
                item("fair") { Caption("Only steps and workouts recorded by your watch or phone count. Anything typed in by hand is left out.", color = th.textFaint) }
            }
        }

        // ------------------------------------------------------------ sheets (cover the whole page)
        GlassSheet(visible = sheet == Sheet.AVATAR, onDismiss = { sheet = Sheet.NONE }) {
            AvatarPickerContent(container, myName, myColor) { sheet = Sheet.NONE }
        }
        GlassSheet(visible = sheet == Sheet.PROFILE && openFriend != null, onDismiss = { sheet = Sheet.NONE }) {
            openFriend?.let { f ->
                FriendProfileContent(container, f, onRemove = {
                    scope.launch { runCatching { social.removeFriend(f.uid) }; sheet = Sheet.NONE; toaster.show("Removed ${f.name}"); refresh() }
                }, onBlocked = { sheet = Sheet.NONE; refresh() })
            }
        }
        GlassSheet(visible = sheet == Sheet.ADD, onDismiss = { sheet = Sheet.NONE }) {
            AddFriendsContent(container, profile) { refresh() }
        }
        GlassSheet(visible = sheet == Sheet.SETTINGS, onDismiss = { sheet = Sheet.NONE }) {
            FriendsSettingsContent(container, profile) { refresh() }
        }
        GlassSheet(visible = sheet == Sheet.NEW_CHALLENGE, onDismiss = { sheet = Sheet.NONE }) {
            NewChallengeContent(container, snap?.friends.orEmpty()) { sheet = Sheet.NONE; refresh() }
        }
        GlassSheet(visible = sheet == Sheet.CHALLENGE && openChallenge != null, onDismiss = { sheet = Sheet.NONE }) {
            openChallenge?.let { ch -> ChallengeContent(container, ch, ranked) { sheet = Sheet.NONE; refresh() } }
        }
        GlassSheet(visible = sheet == Sheet.GLOBAL, onDismiss = { sheet = Sheet.NONE }) { GlobalBoardContent(container) }
    }
}

private fun Modifier.graphicsRotate(deg: Float): Modifier = this.then(Modifier.graphicsLayer { rotationZ = deg })

@Composable
private fun MeStat(value: String, label: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(th.text.copy(alpha = 0.06f)).padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        com.myfit.tracker.ui.components.FitText(value, FitType.section, th.text)
        Caption(label)
    }
}

/** Top three, 2nd – 1st – 3rd, with steps under each. */
@Composable
internal fun Podium(top: List<FriendCard>, onOpen: (FriendCard) -> Unit) {
    val th = LocalFitTheme.current
    val order = listOfNotNull(top.getOrNull(1)?.let { it to 2 }, top.getOrNull(0)?.let { it to 1 }, top.getOrNull(2)?.let { it to 3 })
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            order.forEach { (f, place) ->
                val medal = when (place) { 1 -> GOLD; 2 -> SILVER; else -> BRONZE }
                val size = if (place == 1) 76.dp else 60.dp
                Column(Modifier.weight(1f).clickableNoRipple { onOpen(f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        UserAvatar(f.avatar, f.photo, f.name, f.color, size, Modifier.padding(bottom = 10.dp), ring = medal, gphoto = f.gphoto, seed = f.uid)
                        Box(Modifier.size(24.dp).clip(CircleShape).background(medal), contentAlignment = Alignment.Center) {
                            Text("$place", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (f.me) "You" else f.name.substringBefore(' '), style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    com.myfit.tracker.ui.components.FitText(Fmt.int(f.weekSteps.toDouble()), FitType.caption, th.textDim)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(if (place == 1) 34.dp else if (place == 2) 24.dp else 16.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(Brush.verticalGradient(listOf(medal.copy(alpha = 0.55f), medal.copy(alpha = 0.12f)))))
                }
            }
        }
    }
}

@Composable
internal fun RaceRow(rank: Int, f: FriendCard, frac: Float, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val medal = when (rank) { 1 -> GOLD; 2 -> SILVER; 3 -> BRONZE; else -> null }
    val bar by animateFloatAsState(frac.coerceIn(0.02f, 1f), tween(700), label = "bar")
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Row(Modifier.then(if (f.me) Modifier.background(th.accent.copy(alpha = 0.12f)) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = FitType.label, color = medal ?: th.textDim, modifier = Modifier.widthIn(min = 22.dp))
            UserAvatar(f.avatar, f.photo, f.name, f.color, 42.dp, gphoto = f.gphoto, seed = f.uid)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f.me) "You" else f.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    f.level?.let { LevelChip(it) }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(bar).height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(th.accent, th.accentBright))))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.int(f.weekSteps.toDouble()), style = FitType.label, color = th.text)
                Caption(if (f.me) "steps" else ago(f.updatedAt))
            }
        }
    }
}

@Composable
internal fun ChallengeCard(ch: Challenge, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val today = com.myfit.tracker.domain.Clock.today()
    val end = runCatching { java.time.LocalDate.parse(ch.end) }.getOrNull()
    val left = end?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt() } ?: 0
    Glass(Modifier.width(210.dp).height(120.dp), shape = RoundedCornerShape(22.dp), onClick = onClick) {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(th.accent.copy(alpha = 0.22f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Icon(Duo.Flag, null, tint = th.accentBright, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(ch.title, style = FitType.label, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Caption("${ch.metric.label} · ${ch.members.size} people · " + when { left < 0 -> "finished"; left == 0 -> "last day"; else -> "$left days left" })
        }
    }
}

@Composable
private fun ActivityStrip(container: AppContainer, people: List<FriendCard>, key: Int) {
    val th = LocalFitTheme.current
    val items by produceState<List<com.myfit.tracker.social.Social.FeedItem>?>(null, key) { value = runCatching { container.social.feed(8) }.getOrDefault(emptyList()) }
    val byId = people.associateBy { it.uid }
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            val l = items
            when {
                l == null -> Caption("Loading…", Modifier.padding(vertical = 8.dp))
                l.isEmpty() -> Caption("Nothing yet — 10,000-step days, finished workouts and level-ups show up here.", Modifier.padding(vertical = 8.dp))
                else -> l.take(5).forEach { f ->
                    val p = byId[f.uid]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(p?.avatar, p?.photo, f.name, p?.color ?: 0xFF4C8DFFL, 34.dp, gphoto = p?.gphoto, seed = f.uid)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (f.me) "You " else f.name + " ") + f.text, style = FitType.body, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Caption(feedAgo(f.at))
                        }
                        Icon(when (f.kind) { "workout" -> Duo.FitnessCenter; "level" -> Duo.EmojiEvents; else -> Duo.DirectionsWalk }, null, tint = th.accentBright, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Add friends: requests, search by @username / ID, your invite QR, add by code. */
@Composable
private fun AddFriendsContent(container: AppContainer, profile: Profile?, onChanged: () -> Unit) {
    val th = LocalFitTheme.current
    val social = container.social
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    var key by remember { mutableIntStateOf(0) }
    Text("Add friends", style = FitType.title, color = th.text)
    Spacer(Modifier.height(12.dp))
    FindFriendsCard(social, key) { key++; onChanged() }
    Spacer(Modifier.height(12.dp))
    InviteCard(profile)
    Spacer(Modifier.height(12.dp))
    var code by remember { mutableStateOf("") }
    Text("Have their MyFit ID?", style = FitType.label, color = th.textDim)
    Spacer(Modifier.height(6.dp))
    Field(code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) }, "6-character ID")
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentButton("Add", {
            scope.launch {
                runCatching { social.addFriendByCode(code) }
                    .onSuccess { toaster.show("Added ${it.name}"); code = ""; onChanged() }
                    .onFailure { toaster.show(it.message ?: "Couldn't add") }
            }
        }, Modifier.weight(1f), icon = Duo.Add, height = 44.dp)
        GlassButton("Scan QR", { FriendActions.scan(container, ctx, scope, { toaster.show(it) }) { onChanged() } }, Modifier.weight(1f), icon = Duo.Scan, height = 44.dp)
    }
    Spacer(Modifier.height(10.dp))
}

/** Name, username, global board visibility, sign out, delete. */
@Composable
private fun FriendsSettingsContent(container: AppContainer, p: Profile?, onChanged: () -> Unit) {
    val th = LocalFitTheme.current
    val social = container.social
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    Text("Friends settings", style = FitType.title, color = th.text)
    Spacer(Modifier.height(12.dp))
    var name by remember(p?.name) { mutableStateOf(p?.name ?: "") }
    Text("Display name", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    Field(name, { name = it.take(24) }, "Display name")
    if (p != null && name.isNotBlank() && name != p.name) {
        Spacer(Modifier.height(8.dp))
        AccentButton("Save name", { scope.launch { runCatching { social.updateProfile(name = name) }.onSuccess { onChanged(); toaster.show("Name saved") }.onFailure { toaster.show("Couldn't save — check your connection") } } }, Modifier.fillMaxWidth(), height = 44.dp)
    }
    Spacer(Modifier.height(12.dp))
    UsernameCard(social, p) { onChanged() }
    Spacer(Modifier.height(8.dp))
    if (p != null) ToggleRow("Show me on the global leaderboard", "Only your name and weekly totals. Friends always see you.", p.isPublic) { v ->
        scope.launch { runCatching { social.updateProfile(isPublic = v) }.onFailure { toaster.show("Couldn't update — check your connection") }; onChanged() }
    }
    Spacer(Modifier.height(12.dp))
    GlassButton("Sign out", { social.signOut() }, Modifier.fillMaxWidth(), icon = Duo.ArrowBack, height = 46.dp)
    var confirm by remember { mutableStateOf(false) }
    Text(if (confirm) "Tap again to permanently delete your online account" else "Delete online account", style = FitType.label, color = th.danger,
        modifier = Modifier.clickableNoRipple {
            if (!confirm) confirm = true
            else scope.launch {
                runCatching { social.deleteAccount() }.onSuccess { toaster.show("Online account deleted — your logs on this phone are untouched") }
                    .onFailure { toaster.show(it.message ?: "Please sign in again, then delete") }
            }
        }.padding(vertical = 12.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewChallengeContent(container: AppContainer, friends: List<FriendCard>, onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var title by remember { mutableStateOf("") }
    var m by remember { mutableStateOf(Metric.STEPS) }
    var days by remember { mutableIntStateOf(7) }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    Text("New challenge", style = FitType.title, color = th.text)
    Spacer(Modifier.height(10.dp))
    Field(title, { title = it.take(40) }, "Name (e.g. Weekend step-off)")
    Spacer(Modifier.height(12.dp))
    Text("Compete on", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Metric.entries.forEach { x -> GlassChip(x.label, m == x, { m = x }) } }
    Spacer(Modifier.height(12.dp))
    Text("Length", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "1 day", 3 to "3 days", 7 to "1 week", 30 to "30 days").forEach { (d, l) -> GlassChip(l, days == d, { days = d }) }
    }
    Spacer(Modifier.height(12.dp))
    Text("Who's in", style = FitType.label, color = th.textDim); Spacer(Modifier.height(8.dp))
    if (friends.isEmpty()) Caption("Add a friend first.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        friends.forEach { f ->
            val sel = f.uid in picked
            Column(Modifier.width(64.dp).clickableNoRipple { if (sel) picked.remove(f.uid) else picked.add(f.uid) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(f.avatar, f.photo, f.name, f.color, 52.dp, ring = if (sel) th.accentBright else null, gphoto = f.gphoto, seed = f.uid)
                    if (sel) Box(Modifier.size(18.dp).clip(CircleShape).background(th.accentBright), contentAlignment = Alignment.Center) { Icon(Duo.Check, null, tint = Color(0xFF14161B), modifier = Modifier.size(12.dp)) }
                }
                Text(f.name.substringBefore(' '), style = FitType.caption, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    AccentButton("Start challenge", {
        if (picked.isEmpty()) { toaster.show("Pick at least one friend"); return@AccentButton }
        scope.launch {
            runCatching { container.social.createChallenge(title, m, days, picked.toList()) }
                .onSuccess { toaster.show("Challenge started — counting from today"); onDone() }
                .onFailure { toaster.show(it.message ?: "Couldn't create") }
        }
    }, Modifier.fillMaxWidth(), icon = Duo.Flag)
}

@Composable
private fun ChallengeContent(container: AppContainer, ch: Challenge, people: List<FriendCard>, onLeft: () -> Unit) {
    val th = LocalFitTheme.current
    val scope = rememberCoroutineScope()
    val rows by produceState<List<ChallengeRow>?>(null, ch.id) { value = runCatching { container.social.standings(ch) }.getOrElse { emptyList() } }
    val byId = people.associateBy { it.uid }
    Text(ch.title, style = FitType.title, color = th.text)
    Caption("${ch.metric.label} · ${ch.start} → ${ch.end}")
    Spacer(Modifier.height(12.dp))
    val r = rows
    if (r == null) Caption("Loading…")
    else {
        val top = r.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
        r.forEachIndexed { i, row ->
            val p = byId[row.uid]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = FitType.label, color = when (i) { 0 -> GOLD; 1 -> SILVER; 2 -> BRONZE; else -> th.textDim }, modifier = Modifier.width(22.dp))
                UserAvatar(p?.avatar, p?.photo, row.name, p?.color ?: 0xFF4C8DFFL, 38.dp, gphoto = p?.gphoto, seed = row.uid)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (row.me) "You" else row.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxWidth((row.value / top).toFloat().coerceIn(0.02f, 1f)).height(5.dp).clip(CircleShape).background(th.accentBright))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(formatMetric(ch.metric, row.value), style = FitType.label, color = th.text)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Caption("Updates whenever each person's app syncs (about every 30 minutes).", color = th.textFaint)
    Text("Leave challenge", style = FitType.label, color = th.danger, modifier = Modifier.clickableNoRipple {
        scope.launch { runCatching { container.social.leaveChallenge(ch.id) }; onLeft() }
    }.padding(vertical = 12.dp))
}

@Composable
private fun GlobalBoardContent(container: AppContainer) {
    val th = LocalFitTheme.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var metric by remember { mutableStateOf(Metric.STEPS) }
    val board by produceState<List<BoardRow>?>(null, metric) { value = null; value = runCatching { container.social.globalBoard(metric) }.getOrDefault(emptyList()) }
    Text("Global leaderboard", style = FitType.title, color = th.text)
    Caption("This week · people who chose to appear publicly")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric.entries.forEach { m -> GlassChip(m.label, metric == m, { metric = m }) } }
    Spacer(Modifier.height(10.dp))
    val b = board
    when {
        b == null -> Caption("Loading…")
        b.isEmpty() -> Caption("No one on the global board yet this week.")
        else -> b.take(50).forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().then(if (r.me) Modifier.clip(RoundedCornerShape(14.dp)).background(th.accent.copy(alpha = 0.12f)) else Modifier).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = FitType.label, color = when (i) { 0 -> GOLD; 1 -> SILVER; 2 -> BRONZE; else -> th.textDim }, modifier = Modifier.width(28.dp))
                UserAvatar(r.avatar, null, r.name, r.color, 34.dp, gphoto = r.gphoto, seed = r.uid)
                Spacer(Modifier.width(10.dp))
                Text(if (r.me) "${r.name} (you)" else r.name, style = FitType.label, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMetric(metric, r.value), style = FitType.label, color = th.text)
                if (!r.me) Icon(Duo.Flag, "Report ${r.name}", tint = th.textFaint, modifier = Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).clickableNoRipple {
                    scope.launch {
                        runCatching { container.social.report(r.uid, "Offensive name (global board)", "") }
                            .onSuccess { toaster.show("Reported ${r.name} — thanks, we'll review it") }
                            .onFailure { toaster.show("Couldn't send — check your connection") }
                    }
                }.padding(5.dp))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

internal fun ago(t: Long): String {
    if (t <= 0) return "not synced yet"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when { m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
}
