package com.myfit.tracker.social

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.HealthSync
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** What people compete on. Only things a watch or phone records by itself. */
enum class Metric(val key: String, val label: String, val unit: String) {
    STEPS("steps", "Steps", "steps"),
    ACTIVE("activeMin", "Workout minutes", "min"),
    DISTANCE("distanceM", "Distance", "km"),
}

data class Profile(val uid: String, val name: String, val code: String, val isPublic: Boolean, val color: Long, val username: String = "")
/** A search hit: [viaId] = matched by their MyFit ID (instant add), else by username (sends a friend request). */
data class Found(val profile: Profile, val viaId: Boolean, val isFriend: Boolean)
data class FriendRequest(val uid: String, val name: String, val username: String)
data class BoardRow(val uid: String, val name: String, val color: Long, val value: Double, val me: Boolean, val level: Int? = null, val mascot: String? = null)
data class Challenge(
    val id: String, val title: String, val metric: Metric, val start: String, val end: String,
    val creator: String, val members: List<String>,
)
data class ChallengeRow(val uid: String, val name: String, val value: Double, val me: Boolean)
/** A friend's progress in an Arena challenge (goals are personal, so the race is on % and finish time). */
data class RaceRow(val uid: String, val name: String, val pct: Double, val value: Double, val doneAt: String?, val mascot: String?, val level: Int?, val me: Boolean)

/**
 * Accounts (Google / email), friends, weekly leaderboards and challenges on Firebase.
 *
 * Fair play: everything uploaded comes from [HealthSync.autoDays] — device-recorded steps, distance
 * and workout minutes with hand-typed entries removed. Logged gym sets are never used. The server
 * rules (firestore.rules) only let you write your own numbers and reject impossible values.
 */
class Social(private val c: AppContainer) {
    private val ctx: Context get() = c.app

    /** False when this build has no Firebase config — every online screen shows a friendly notice instead. */
    val available: Boolean by lazy { com.myfit.tracker.BuildConfig.SOCIAL && runCatching { FirebaseApp.getApps(ctx).isNotEmpty() }.getOrDefault(false) }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user
    val lastSync = MutableStateFlow<String?>(null)

    @Volatile private var started = false
    fun start() {
        if (!available || started) return
        started = true
        _user.value = auth.currentUser
        auth.addAuthStateListener { _user.value = it.currentUser }
    }

    // ------------------------------------------------------------------ sign-in

    /** The Google "web client id" generated from google-services.json (looked up by name so builds without it still compile). */
    fun webClientId(): String? {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else null
    }

    suspend fun signInWithGoogleToken(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        ensureProfile()
    }

    suspend fun signInEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        ensureProfile()
    }

    suspend fun createEmail(email: String, password: String, name: String) {
        val r = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        r.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
        ensureProfile(name.trim())
    }

    suspend fun resetPassword(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }

    fun signOut() { auth.signOut() }

    /** Deletes your public data and account. */
    suspend fun deleteAccount() {
        val u = auth.currentUser ?: return
        val last = u.metadata?.lastSignInTimestamp ?: 0L
        if (System.currentTimeMillis() - last > 5 * 60_000L) throw IllegalStateException("For safety, sign out and sign in again, then delete within 5 minutes.")
        deleteCloudData()
        u.delete().await()
    }

    /** Removes everything MyFit stored about you on the server (profile, totals, friends, feed). Keeps you signed in. */
    suspend fun deleteCloudData() {
        val u = auth.currentUser ?: return
        val p = profile()
        runCatching {
            val items = db.collection("feed").document(u.uid).collection("items").get().await()
            items.documents.forEach { runCatching { it.reference.delete().await() } }
        }
        runCatching { db.collection("weeklyPublic").document(weekKey()).collection("entries").document(u.uid).delete().await() }
        runCatching { db.collection("weekly").document(weekKey()).collection("entries").document(u.uid).delete().await() }
        runCatching { friends().forEach { removeFriend(it.uid) } }
        p?.code?.takeIf { it.isNotBlank() }?.let { runCatching { db.collection("codes").document(it).delete().await() } }
        p?.username?.takeIf { it.isNotBlank() }?.let { runCatching { db.collection("usernames").document(it).delete().await() } }
        runCatching { db.collection("users").document(u.uid).collection("requests").get().await().documents.forEach { runCatching { it.reference.delete().await() } } }
        runCatching { db.collection("users").document(u.uid).collection("private").document("me").delete().await() }
        runCatching { db.collection("users").document(u.uid).delete().await() }
    }

    // ------------------------------------------------------------------ profile

    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // no 0/O/1/I
        return (1..6).map { chars.random() }.joinToString("")
    }

    /** Creates your profile and friend code the first time you sign in. */
    suspend fun ensureProfile(nameHint: String? = null): Profile? {
        val u = auth.currentUser ?: return null
        val ref = db.collection("users").document(u.uid)
        val snap = ref.get().await()
        if (snap.exists()) return toProfile(u.uid, snap.data ?: emptyMap(), myCode())
        var code = newCode()
        repeat(5) { if (db.collection("codes").document(code).get().await().exists()) code = newCode() }
        val localName = runCatching { c.profileRepo.profile.firstOrNull()?.name }.getOrNull()
        val name = (nameHint ?: u.displayName ?: localName ?: u.email?.substringBefore('@') ?: "Athlete").take(24)
        val color = listOf(0xFFFF7A1AL, 0xFF4C8DFFL, 0xFF2FD37AL, 0xFFB57CFFL, 0xFFFF4F86L, 0xFFFFC857L).random()
        val data = mapOf("name" to name, "public" to true, "color" to color, "createdAt" to FieldValue.serverTimestamp())
        ref.set(data).await()
        db.collection("codes").document(code).set(mapOf("uid" to u.uid)).await()
        ref.collection("private").document("me").set(mapOf("code" to code)).await()
        return Profile(u.uid, name, code, true, color)
    }

    private fun toProfile(uid: String, m: Map<String, Any?>, code: String = "") = Profile(
        uid, (m["name"] as? String) ?: "Athlete", code, (m["public"] as? Boolean) ?: true, (m["color"] as? Number)?.toLong() ?: 0xFFFF7A1AL,
        (m["username"] as? String).orEmpty(),
    )

    /** Your own friend code (kept in a private document only you can read). */
    private suspend fun myCode(): String {
        val u = auth.currentUser ?: return ""
        val priv = db.collection("users").document(u.uid).collection("private").document("me")
        priv.get().await().getString("code")?.let { return it }
        // first run after the code moved to the private doc: make a new one
        var code = newCode()
        repeat(5) { if (db.collection("codes").document(code).get().await().exists()) code = newCode() }
        db.collection("codes").document(code).set(mapOf("uid" to u.uid)).await()
        priv.set(mapOf("code" to code)).await()
        return code
    }

    suspend fun profile(uid: String? = null): Profile? {
        val me = auth.currentUser?.uid
        val id = uid ?: me ?: return null
        val s = db.collection("users").document(id).get().await()
        return if (s.exists()) toProfile(id, s.data ?: emptyMap(), if (id == me) myCode() else "") else null
    }

    suspend fun updateProfile(name: String? = null, isPublic: Boolean? = null) {
        val u = auth.currentUser ?: return
        val m = buildMap<String, Any> { name?.let { put("name", it.trim().take(24)) }; isPublic?.let { put("public", it) } }
        if (m.isNotEmpty()) db.collection("users").document(u.uid).set(m, SetOptions.merge()).await()
        if (isPublic == false) runCatching { db.collection("weeklyPublic").document(weekKey()).collection("entries").document(u.uid).delete().await() }
        uploadNow(0)
    }

    // ------------------------------------------------------------------ friends

    suspend fun addFriendByCode(code: String): Profile {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first")
        val c2 = code.trim().uppercase()
        val owner = db.collection("codes").document(c2).get().await().getString("uid") ?: throw IllegalArgumentException("No one has the code $c2")
        if (owner == u.uid) throw IllegalArgumentException("That's your own code")
        val now = FieldValue.serverTimestamp()
        db.collection("users").document(u.uid).collection("friends").document(owner).set(mapOf("since" to now)).await()
        db.collection("users").document(owner).collection("friends").document(u.uid).set(mapOf("since" to now, "code" to c2)).await()
        return profile(owner) ?: Profile(owner, "Friend", c2, false, 0xFF4C8DFFL)
    }

    // ------------------------------------------------------------------ username + MyFit ID

    val USERNAME = Regex("^[a-z0-9_.]{3,20}$")
    private val ID_RE = Regex("^[A-HJ-NP-Z2-9]{6}$")

    fun cleanUsername(s: String) = s.trim().removePrefix("@").lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(20)

    /** Null when free (or already yours), else why it can't be used. */
    suspend fun usernameProblem(handle: String): String? {
        val h = cleanUsername(handle)
        if (!USERNAME.matches(h)) return "3–20 letters, numbers, _ or ."
        if (h.startsWith('.') || h.endsWith('.') || ".." in h) return "Can't start or end with a dot"
        if (h in RESERVED) return "That name is reserved"
        val owner = db.collection("usernames").document(h).get().await().getString("uid")
        return if (owner == null || owner == auth.currentUser?.uid) null else "@$h is taken"
    }
    private val RESERVED = setOf("admin", "myfit", "myfittracker", "support", "official", "pip", "help", "moderator", "staff", "root")

    /** Claims a unique @username (atomically) and releases your old one. */
    suspend fun setUsername(handle: String): String {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first")
        val h = cleanUsername(handle)
        usernameProblem(h)?.let { throw IllegalArgumentException(it) }
        val me = db.collection("users").document(u.uid)
        val old = me.get().await().getString("username").orEmpty()
        if (old == h) return h
        db.runTransaction { tx ->
            val ref = db.collection("usernames").document(h)
            val cur = tx.get(ref)
            if (cur.exists() && cur.getString("uid") != u.uid) throw IllegalArgumentException("@$h was just taken")
            if (!cur.exists()) tx.set(ref, mapOf("uid" to u.uid))
            tx.set(me, mapOf("username" to h), SetOptions.merge())
            if (old.isNotBlank() && old != h) tx.delete(db.collection("usernames").document(old))
            h
        }.await()
        return h
    }

    /**
     * Search by @username (prefix) or by MyFit ID (the 6-character code). An ID match can be added straight away;
     * a username match gets a friend request they must accept.
     */
    suspend fun search(query: String): List<Found> {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first")
        val raw = query.trim()
        if (raw.length < 2) return emptyList()
        val friendIds = runCatching { db.collection("users").document(u.uid).collection("friends").get().await().documents.map { it.id }.toSet() }.getOrDefault(emptySet())
        val out = LinkedHashMap<String, Found>()
        val asId = raw.uppercase().removePrefix("#")
        if (ID_RE.matches(asId)) {
            db.collection("codes").document(asId).get().await().getString("uid")?.takeIf { it != u.uid }?.let { owner ->
                profile(owner)?.let { out[owner] = Found(it.copy(code = asId), true, owner in friendIds) }
            }
        }
        val h = cleanUsername(raw)
        if (h.length >= 2) {
            val snap = db.collection("users").orderBy("username").startAt(h).endAt(h + "\uf8ff").limit(10).get().await()
            snap.documents.filter { it.id != u.uid && it.id !in out }.forEach { d -> out[d.id] = Found(toProfile(d.id, d.data ?: emptyMap()), false, d.id in friendIds) }
        }
        return out.values.toList()
    }

    /** Asks [uid] to be friends (they see it under Friends → Requests). */
    suspend fun sendRequest(uid: String) {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first")
        val me = profile() ?: throw IllegalStateException("Profile not ready")
        db.collection("users").document(uid).collection("requests").document(u.uid)
            .set(mapOf("name" to me.name.take(24), "username" to me.username, "at" to FieldValue.serverTimestamp())).await()
    }

    suspend fun requests(): List<FriendRequest> {
        val u = auth.currentUser ?: return emptyList()
        return db.collection("users").document(u.uid).collection("requests").get().await().documents.map {
            FriendRequest(it.id, it.getString("name") ?: "Someone", it.getString("username").orEmpty())
        }
    }

    suspend fun acceptRequest(from: String) {
        val u = auth.currentUser ?: return
        val now = FieldValue.serverTimestamp()
        db.collection("users").document(u.uid).collection("friends").document(from).set(mapOf("since" to now)).await()
        db.collection("users").document(from).collection("friends").document(u.uid).set(mapOf("since" to now)).await()
        runCatching { db.collection("users").document(u.uid).collection("requests").document(from).delete().await() }
    }

    suspend fun declineRequest(from: String) {
        val u = auth.currentUser ?: return
        db.collection("users").document(u.uid).collection("requests").document(from).delete().await()
    }

    suspend fun removeFriend(uid: String) {
        val me = auth.currentUser?.uid ?: return
        runCatching { db.collection("users").document(me).collection("friends").document(uid).delete().await() }
        runCatching { db.collection("users").document(uid).collection("friends").document(me).delete().await() }
    }

    suspend fun friends(): List<Profile> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val ids = db.collection("users").document(me).collection("friends").get().await().documents.map { it.id }
        return ids.mapNotNull { runCatching { profile(it) }.getOrNull() }
    }

    // ------------------------------------------------------------------ leaderboards

    fun weekKey(d: LocalDate = Clock.today()): String = "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
    fun weekStart(d: LocalDate = Clock.today()): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun rowValue(m: Map<String, Any?>, metric: Metric): Double = (m[metric.key] as? Number)?.toDouble() ?: 0.0

    /** You + your friends, this week. */
    suspend fun friendsBoard(metric: Metric): List<BoardRow> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val people = listOfNotNull(profile(me)) + friends()
        val col = db.collection("weekly").document(weekKey()).collection("entries")
        return people.map { p ->
            val m = runCatching { col.document(p.uid).get().await().data }.getOrNull()
            BoardRow(p.uid, p.name, p.color, m?.let { rowValue(it, metric) } ?: 0.0, p.uid == me, (m?.get("level") as? Number)?.toInt(), m?.get("mascot") as? String)
        }.sortedByDescending { it.value }
    }

    /** Everyone who chose to appear publicly, this week (top 50). */
    suspend fun globalBoard(metric: Metric): List<BoardRow> {
        val me = auth.currentUser?.uid
        return db.collection("weeklyPublic").document(weekKey()).collection("entries")
            .orderBy(metric.key, Query.Direction.DESCENDING).limit(50).get().await().documents.map { d ->
                BoardRow(d.id, d.getString("name") ?: "Athlete", d.getLong("color") ?: 0xFF4C8DFFL, rowValue(d.data ?: emptyMap(), metric), d.id == me, d.getLong("level")?.toInt(), d.getString("mascot"))
            }
    }

    // ------------------------------------------------------------------ challenges

    suspend fun createChallenge(title: String, metric: Metric, days: Int, friendIds: List<String>): String {
        val me = auth.currentUser?.uid ?: throw IllegalStateException("Sign in first")
        val start = Clock.today(); val end = start.plusDays(days.toLong() - 1)
        val ref = db.collection("challenges").document()
        ref.set(mapOf(
            "title" to title.trim().ifBlank { "${metric.label} challenge" }.take(40), "metric" to metric.key,
            "start" to start.toString(), "end" to end.toString(), "creator" to me, "members" to (listOf(me) + friendIds).distinct(),
            "createdAt" to FieldValue.serverTimestamp(),
        )).await()
        uploadNow(0)
        return ref.id
    }

    suspend fun myChallenges(): List<Challenge> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val friendIds = runCatching { db.collection("users").document(me).collection("friends").get().await().documents.map { it.id }.toSet() }.getOrDefault(emptySet())
        return db.collection("challenges").whereArrayContains("members", me).get().await().documents.mapNotNull { d ->
            val m = Metric.entries.firstOrNull { it.key == d.getString("metric") } ?: return@mapNotNull null
            val start = d.getString("start")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val end = d.getString("end")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val creator = d.getString("creator") ?: return@mapNotNull null
            if (creator != me && creator !in friendIds) return@mapNotNull null   // only challenges from you or your friends
            @Suppress("UNCHECKED_CAST")
            Challenge(d.id, d.getString("title") ?: "", m, start.toString(), end.toString(), creator, ((d.get("members") as? List<*>)?.filterIsInstance<String>()) ?: emptyList())
        }.sortedByDescending { it.end }
    }

    suspend fun standings(ch: Challenge): List<ChallengeRow> {
        val me = auth.currentUser?.uid
        val names = ch.members.associateWith { runCatching { profile(it)?.name }.getOrNull() ?: "Friend" }
        val prog = db.collection("challenges").document(ch.id).collection("progress").get().await().documents.associate { it.id to ((it.get(ch.metric.key) as? Number)?.toDouble() ?: 0.0) }
        return ch.members.map { ChallengeRow(it, names[it] ?: "Friend", prog[it] ?: 0.0, it == me) }.sortedByDescending { it.value }
    }

    suspend fun leaveChallenge(id: String) {
        val me = auth.currentUser?.uid ?: return
        db.collection("challenges").document(id).update("members", FieldValue.arrayRemove(me)).await()
    }

    // ------------------------------------------------------------------ Arena races (friends in the same weekly/monthly challenge)

    private fun race(cid: String) = db.collection("arena").document(cid).collection("members")

    /** Join (or update) your entry in an Arena challenge race. */
    suspend fun raceUpdate(cid: String, pct: Double, value: Double, doneAt: String?, mascot: String, level: Int) {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first")
        val name = runCatching { profile(u.uid)?.name }.getOrNull() ?: (u.displayName ?: "Friend").take(24)
        race(cid).document(u.uid).set(mapOf(
            "name" to name.take(24), "pct" to pct.coerceIn(0.0, 10.0), "value" to value, "doneAt" to (doneAt ?: ""),
            "mascot" to mascot.take(12), "level" to level.coerceIn(1, 60), "updatedAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    suspend fun raceLeave(cid: String) { val u = auth.currentUser ?: return; race(cid).document(u.uid).delete().await() }

    /** You + friends who joined this challenge, first finisher first, then by progress. */
    suspend fun raceStandings(cid: String): List<RaceRow> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val ids = listOf(me) + runCatching { friends().map { it.uid } }.getOrDefault(emptyList())
        val col = race(cid)
        return ids.mapNotNull { id ->
            val m = runCatching { col.document(id).get().await().data }.getOrNull() ?: return@mapNotNull null
            RaceRow(id, m["name"] as? String ?: "Friend", (m["pct"] as? Number)?.toDouble() ?: 0.0, (m["value"] as? Number)?.toDouble() ?: 0.0,
                (m["doneAt"] as? String)?.takeIf { it.isNotBlank() }, m["mascot"] as? String, (m["level"] as? Number)?.toInt(), id == me)
        }.sortedWith(compareBy<RaceRow> { it.doneAt ?: "9999" }.thenByDescending { it.pct })
    }

    // ------------------------------------------------------------------ upload (only auto-recorded data)

    /** Uploads this week's device-recorded totals and your progress in active challenges. Safe to call often. */
    /**
     * Uploads this week's device-recorded totals. Throttled: skipped when the last upload was under [minGapMin]
     * minutes ago, and the Firestore writes are skipped when nothing changed since the last upload.
     */
    suspend fun uploadNow(minGapMin: Int = 60): String? {
        if (!available) return null
        val u = auth.currentUser ?: return null
        val up = ctx.getSharedPreferences("social_upload", android.content.Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (minGapMin > 0 && now - up.getLong("at", 0L) < minGapMin * 60_000L) return lastSync.value
        val p = profile(u.uid) ?: ensureProfile() ?: return null
        val hs = c.healthSync
        val today = Clock.today()
        val challenges = runCatching { myChallenges() }.getOrDefault(emptyList()).filter { !LocalDate.parse(it.end).isBefore(today.minusDays(1)) }
        val earliest = (challenges.map { LocalDate.parse(it.start) } + weekStart()).minOrNull() ?: weekStart()
        val days = hs.autoDays(earliest.coerceAtLeast(today.minusDays(40)), today)
        if (days.isEmpty()) { lastSync.value = "Health Connect isn't connected"; return lastSync.value }
        fun sum(from: LocalDate, to: LocalDate) = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val week = sum(weekStart(), today)
        val entry = mapOf(
            "name" to p.name, "color" to p.color,
            "steps" to week.sumOf { it.steps }, "activeMin" to week.sumOf { it.activeMin },
            "distanceM" to week.sumOf { it.distanceM }.let { Math.round(it).toDouble() },
            "days" to week.size, "updatedAt" to FieldValue.serverTimestamp(),
        )
        val sig = listOf(weekKey(), p.name, p.color, p.isPublic, entry["steps"], entry["activeMin"], entry["distanceM"],
            com.myfit.tracker.ui.arena.ArenaProgress.total(ctx), com.myfit.tracker.ui.arena.ArenaPrefs.partner(ctx).id,
            challenges.joinToString { it.id }, days.firstOrNull { it.date == today }?.steps).joinToString("|")
        up.edit().putLong("at", now).apply()
        if (sig == up.getString("sig", null) && now - up.getLong("sigAt", 0L) < 6 * 3_600_000L) return lastSync.value
        db.collection("weekly").document(weekKey()).collection("entries").document(u.uid).set(entry).await()
        val pub = db.collection("weeklyPublic").document(weekKey()).collection("entries").document(u.uid)
        if (p.isPublic) pub.set(entry).await() else runCatching { pub.delete().await() }
        // Arena level + partner (separate write: harmless if the server rules haven't been updated yet)
        val arena = mapOf("level" to com.myfit.tracker.ui.arena.ArenaProgress.level(com.myfit.tracker.ui.arena.ArenaProgress.total(ctx)).n,
            "mascot" to com.myfit.tracker.ui.arena.ArenaPrefs.partner(ctx).id)
        runCatching { db.collection("weekly").document(weekKey()).collection("entries").document(u.uid).set(arena, com.google.firebase.firestore.SetOptions.merge()).await() }
        if (p.isPublic) runCatching { pub.set(arena, com.google.firebase.firestore.SetOptions.merge()).await() }
        challenges.forEach { ch ->
            val r = sum(LocalDate.parse(ch.start), minOf(LocalDate.parse(ch.end), today))
            val v: Number = when (ch.metric) { Metric.STEPS -> r.sumOf { it.steps }; Metric.ACTIVE -> r.sumOf { it.activeMin }; Metric.DISTANCE -> Math.round(r.sumOf { it.distanceM }).toDouble() }
            runCatching {
                db.collection("challenges").document(ch.id).collection("progress").document(u.uid)
                    .set(mapOf(ch.metric.key to v, "updatedAt" to FieldValue.serverTimestamp())).await()
            }
        }
        // activity feed: a few milestones friends can see (idempotent ids — re-syncing never duplicates)
        runCatching {
            val todaySteps = days.firstOrNull { it.date == today }?.steps ?: 0L
            if (todaySteps >= 10_000) postEvent("steps-$today", "steps", "walked ${java.text.NumberFormat.getIntegerInstance().format(todaySteps)} steps today")
            c.workoutRepo.completedRange(today.toString(), today.toString()).first().forEach { w ->
                postEvent("workout-${w.uuid}", "workout", "finished a workout: ${w.name.take(40)}")
            }
            val lvl = com.myfit.tracker.ui.arena.ArenaProgress.level(com.myfit.tracker.ui.arena.ArenaProgress.total(ctx)).n
            if (lvl > 1) postEvent("level-$lvl", "level", "reached Arena level $lvl")
        }
        up.edit().putString("sig", sig).putLong("sigAt", now).apply()
        lastSync.value = "Synced ${java.time.LocalTime.now().withNano(0).withSecond(0)}"
        return lastSync.value
    }

    // ---------------------------------------------------------------- activity feed
    data class FeedItem(val uid: String, val name: String, val kind: String, val text: String, val at: Long, val me: Boolean)

    suspend fun postEvent(id: String, kind: String, text: String) {
        val u = auth.currentUser ?: return
        val name = profile(u.uid)?.name ?: "Athlete"
        val ref = db.collection("feed").document(u.uid).collection("items").document(id.replace('/', '_').take(80))
        if (ref.get().await().exists()) return
        ref.set(mapOf("kind" to kind, "text" to text.take(120), "name" to name.take(24), "at" to FieldValue.serverTimestamp())).await()
    }

    /** Recent milestones from me and my friends, newest first. */
    suspend fun feed(limit: Int = 40): List<FeedItem> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val people = listOf(me) + friends().map { it.uid }
        return people.flatMap { uid ->
            runCatching {
                db.collection("feed").document(uid).collection("items").orderBy("at", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(10).get().await()
                    .documents.map { d -> FeedItem(uid, d.getString("name") ?: "Friend", d.getString("kind") ?: "", d.getString("text") ?: "", d.getTimestamp("at")?.toDate()?.time ?: 0L, uid == me) }
            }.getOrDefault(emptyList())
        }.sortedByDescending { it.at }.take(limit)
    }
}

