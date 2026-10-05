package com.myfit.tracker.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.social.Found
import com.myfit.tracker.social.FriendRequest
import com.myfit.tracker.social.Profile
import com.myfit.tracker.social.Social
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSearchField
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Your unique @username and MyFit ID, with an inline editor. */
@Composable
fun UsernameCard(social: Social, profile: Profile?, onChanged: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var editing by remember(profile?.username) { mutableStateOf(profile != null && profile.username.isBlank()) }
    var text by remember(profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(text, editing) {
        if (!editing || text.isBlank()) { problem = null; return@LaunchedEffect }
        checking = true; delay(450)
        problem = runCatching { social.usernameProblem(text) }.getOrElse { "Can't check right now" }
        checking = false
    }
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (!editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("@" + (profile?.username ?: "…"), style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption("MyFit ID: ${profile?.code ?: "······"} · friends can find you by either")
                    }
                    GlassButton("Edit", { editing = true }, height = 38.dp)
                }
            } else {
                Text(if (profile?.username.isNullOrBlank()) "Pick your @username" else "Change @username", style = FitType.section, color = th.text)
                Caption("Friends search this to find you. Letters, numbers, _ and . (3–20).")
                Spacer(Modifier.height(10.dp))
                NotesField(text, { text = social.cleanUsername(it) }, "e.g. sohaib.lifts")
                Spacer(Modifier.height(6.dp))
                Caption(when {
                    text.isBlank() -> " "
                    checking -> "Checking…"
                    problem != null -> problem!!
                    else -> "@$text is available"
                }, color = if (problem != null) th.warning else th.success)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!profile?.username.isNullOrBlank()) GlassButton("Cancel", { editing = false; text = profile?.username.orEmpty() }, Modifier.weight(1f), height = 44.dp)
                    AccentButton("Save", {
                        scope.launch {
                            runCatching { social.setUsername(text) }
                                .onSuccess { toaster.show("You're @$it"); editing = false; onChanged() }
                                .onFailure { toaster.show(it.message ?: "Couldn't save") }
                        }
                    }, Modifier.weight(1f), height = 44.dp, enabled = text.isNotBlank() && problem == null && !checking)
                }
            }
        }
    }
}

/** Search by @username or MyFit ID, plus incoming friend requests. */
@Composable
fun FindFriendsCard(social: Social, refreshKey: Int, onChanged: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Found>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val requested = remember { mutableStateListOf<String>() }
    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    LaunchedEffect(refreshKey) { requests = runCatching { social.requests() }.getOrDefault(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = null; return@LaunchedEffect }
        searching = true; delay(400)
        results = runCatching { social.search(q) }.getOrElse { toaster.show(it.message ?: "Search failed"); emptyList() }
        searching = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (requests.isNotEmpty()) Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Friend requests · ${requests.size}", style = FitType.section, color = th.text)
                requests.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.name, 0xFF4C8DFFL, 38)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.username.isNotBlank()) Caption("@${r.username}")
                        }
                        GlassButton("Decline", { scope.launch { runCatching { social.declineRequest(r.uid) }; requests = requests - r } }, height = 36.dp)
                        Spacer(Modifier.width(6.dp))
                        AccentButton("Accept", {
                            scope.launch {
                                runCatching { social.acceptRequest(r.uid) }
                                    .onSuccess { toaster.show("You and ${r.name} are now friends"); requests = requests - r; onChanged() }
                                    .onFailure { toaster.show(it.message ?: "Couldn't accept") }
                            }
                        }, height = 36.dp)
                    }
                }
            }
        }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Find friends", style = FitType.section, color = th.text)
                Caption("Search their @username or MyFit ID.")
                Spacer(Modifier.height(10.dp))
                GlassSearchField(q, { q = it.take(24) }, "@username or ID")
                val r = results
                if (searching) { Spacer(Modifier.height(8.dp)); Caption("Searching…") }
                else if (r != null && r.isEmpty()) { Spacer(Modifier.height(8.dp)); Caption("No one found. Check the spelling, or share your invite instead.") }
                r?.forEach { f ->
                    Spacer(Modifier.height(10.dp))
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(f.profile.name, f.profile.color, 38)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.profile.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption(listOfNotNull(f.profile.username.takeIf { it.isNotBlank() }?.let { "@$it" }, if (f.viaId) "ID match" else null).joinToString(" · "))
                            }
                            when {
                                f.isFriend -> Caption("Friends ✓", color = th.success)
                                f.profile.uid in requested -> Caption("Requested")
                                f.viaId -> AccentButton("Add", {
                                    scope.launch {
                                        runCatching { social.addFriendByCode(f.profile.code) }
                                            .onSuccess { toaster.show("Added ${it.name}"); q = ""; onChanged() }
                                            .onFailure { toaster.show(it.message ?: "Couldn't add") }
                                    }
                                }, height = 36.dp)
                                else -> GlassButton("Request", {
                                    scope.launch {
                                        runCatching { social.sendRequest(f.profile.uid) }
                                            .onSuccess { requested += f.profile.uid; toaster.show("Request sent to ${f.profile.name}") }
                                            .onFailure { toaster.show(it.message ?: "Couldn't send") }
                                    }
                                }, height = 36.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Caption("An ID match is added straight away (having someone's ID means they shared it). A username match gets a request they accept.", color = th.textFaint)
            }
        }
    }
}
