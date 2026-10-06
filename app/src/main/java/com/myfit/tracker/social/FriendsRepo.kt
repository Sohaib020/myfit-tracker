package com.myfit.tracker.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.myfit.tracker.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Your profile picture: a ready-made avatar (an id from assets/avatars/catalog.json) or your own photo.
 * The photo is kept on the phone; only a tiny 128 px copy (~6–10 KB) goes online, readable by friends only.
 */
object MyAvatar {
    const val PHOTO = "photo"
    val version = MutableStateFlow(0)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("my_avatar", Context.MODE_PRIVATE)
    fun id(ctx: Context): String = prefs(ctx).getString("id", "") ?: ""
    fun photoFile(ctx: Context) = File(ctx.filesDir, "my_avatar.jpg")

    fun setPreset(ctx: Context, id: String) {
        prefs(ctx).edit().putString("id", id).apply(); version.value++
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().putString("id", "").apply(); runCatching { photoFile(ctx).delete() }; version.value++
    }

    /** Centre-crops the picked image to a square, keeps a 320 px copy and makes it your picture. */
    suspend fun setPhoto(ctx: Context, uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val src = if (android.os.Build.VERSION.SDK_INT >= 28)
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val s = info.size; val scale = 1024f / maxOf(s.width, s.height)
                    if (scale < 1f) d.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            else ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            val side = minOf(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 320, 320, true)
            photoFile(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            prefs(ctx).edit().putString("id", PHOTO).apply()
            version.value++
            true
        }.getOrDefault(false)
    }

    /** Tiny base64 JPEG for friends (null when you use a ready-made avatar). */
    fun photoForUpload(ctx: Context): String? {
        if (id(ctx) != PHOTO) return null
        val f = photoFile(ctx); if (!f.exists()) return null
        return runCatching {
            val b = BitmapFactory.decodeFile(f.absolutePath)
            val small = Bitmap.createScaledBitmap(b, 128, 128, true)
            val bos = ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG, 72, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()?.takeIf { it.length < 28_000 }
    }
}

/** Everything the Friends page shows about one person (cached on the phone so it opens instantly). */
data class FriendCard(
    val uid: String, val name: String, val username: String, val color: Long, val avatar: String, val photo: String?,
    val level: Int?, val stars: Int?, val mascot: String?, val weekSteps: Long, val weekActiveMin: Long, val weekWorkouts: Int,
    val journeys: List<String>, val rewards: List<String>, val me: Boolean, val updatedAt: Long,
)

data class FriendsSnap(val me: FriendCard?, val friends: List<FriendCard>, val requests: Int, val fetchedAt: Long) {
    /** You + friends, most steps first. */
    val ranked: List<FriendCard> get() = (listOfNotNull(me) + friends).sortedByDescending { it.weekSteps }
}

/**
 * Friends data with a local cache: the page shows the last known numbers straight away and refreshes in the
 * background (on open, and from the periodic health sync), then updates in place.
 */
class FriendsRepo(private val c: AppContainer) {
    private val file get() = File(c.app.filesDir, "friends_cache.json")
    private val _snap = MutableStateFlow<FriendsSnap?>(null)
    val snap: StateFlow<FriendsSnap?> = _snap
    val refreshing = MutableStateFlow(false)
    private val mutex = Mutex()
    @Volatile private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (_snap.value == null) _snap.value = runCatching { if (file.exists()) fromJson(JSONObject(file.readText())) else null }.getOrNull()
    }

    fun clear() { _snap.value = null; runCatching { file.delete() } }

    /** Re-fetches everyone in parallel. Skipped when the last fetch was under [minGapSec] seconds ago. */
    suspend fun refresh(minGapSec: Int = 30) {
        ensureLoaded()
        if (!c.social.available) return
        val auth = FirebaseAuth.getInstance(); val me = auth.currentUser?.uid ?: return
        val last = _snap.value
        if (last != null && last.me?.uid == me && System.currentTimeMillis() - last.fetchedAt < minGapSec * 1000L) return
        mutex.withLock {
            refreshing.value = true
            try {
                val db = FirebaseFirestore.getInstance()
                val week = c.social.weekKey()
                val ids = db.collection("users").document(me).collection("friends").get().await().documents.map { it.id }
                val cards = coroutineScope { (listOf(me) + ids).map { uid -> async { runCatching { card(db, week, uid, uid == me) }.getOrNull() } }.awaitAll() }.filterNotNull()
                val reqs = runCatching { db.collection("users").document(me).collection("requests").get().await().size() }.getOrDefault(0)
                val s = FriendsSnap(cards.firstOrNull { it.me }, cards.filter { !it.me }, reqs, System.currentTimeMillis())
                _snap.value = s
                withContext(Dispatchers.IO) { runCatching { file.writeText(toJson(s).toString()) } }
            } finally { refreshing.value = false }
        }
    }

    private suspend fun card(db: FirebaseFirestore, week: String, uid: String, me: Boolean): FriendCard? = coroutineScope {
        val u = async { db.collection("users").document(uid).get().await() }
        val w = async { runCatching { db.collection("weekly").document(week).collection("entries").document(uid).get().await() }.getOrNull() }
        val a = async { runCatching { db.collection("arenaProfile").document(uid).get().await() }.getOrNull() }
        val ud = u.await(); if (!ud.exists()) return@coroutineScope null
        val wd = w.await()?.takeIf { it.exists() }; val ad = a.await()?.takeIf { it.exists() }
        @Suppress("UNCHECKED_CAST")
        fun strs(k: String) = (ad?.get(k) as? List<Any?>)?.filterIsInstance<String>().orEmpty()
        val upd = maxOf(wd?.getTimestamp("updatedAt")?.toDate()?.time ?: 0L, ad?.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
        FriendCard(
            uid, ud.getString("name") ?: "Friend", ud.getString("username").orEmpty(), ud.getLong("color") ?: 0xFF4C8DFFL,
            ud.getString("avatar").orEmpty(), ad?.getString("photo"),
            (ad?.getLong("level") ?: wd?.getLong("level"))?.toInt(), ad?.getLong("stars")?.toInt(), ad?.getString("mascot") ?: wd?.getString("mascot"),
            wd?.getLong("steps") ?: ad?.getLong("weekSteps") ?: 0L, wd?.getLong("activeMin") ?: ad?.getLong("weekActiveMin") ?: 0L,
            ad?.getLong("weekWorkouts")?.toInt() ?: 0, strs("journeys"), strs("rewards"), me, upd,
        )
    }

    // ---------------------------------------------------------------- cache (de)serialisation
    private fun toJson(s: FriendsSnap) = JSONObject().apply {
        s.me?.let { put("me", cardJson(it)) }
        put("friends", JSONArray().apply { s.friends.forEach { put(cardJson(it)) } })
        put("requests", s.requests); put("at", s.fetchedAt)
    }
    private fun cardJson(f: FriendCard) = JSONObject().apply {
        put("uid", f.uid); put("name", f.name); put("username", f.username); put("color", f.color); put("avatar", f.avatar)
        f.photo?.let { put("photo", it) }; f.level?.let { put("level", it) }; f.stars?.let { put("stars", it) }; f.mascot?.let { put("mascot", it) }
        put("steps", f.weekSteps); put("active", f.weekActiveMin); put("workouts", f.weekWorkouts)
        put("journeys", JSONArray(f.journeys)); put("rewards", JSONArray(f.rewards)); put("me", f.me); put("upd", f.updatedAt)
    }
    private fun fromJson(o: JSONObject): FriendsSnap {
        fun card(j: JSONObject): FriendCard {
            fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            return FriendCard(j.getString("uid"), j.optString("name", "Friend"), j.optString("username"), j.optLong("color", 0xFF4C8DFFL), j.optString("avatar"),
                j.optString("photo").ifBlank { null }, if (j.has("level")) j.getInt("level") else null, if (j.has("stars")) j.getInt("stars") else null,
                j.optString("mascot").ifBlank { null }, j.optLong("steps"), j.optLong("active"), j.optInt("workouts"), list("journeys"), list("rewards"),
                j.optBoolean("me"), j.optLong("upd"))
        }
        val fs = o.optJSONArray("friends")?.let { a -> (0 until a.length()).map { card(a.getJSONObject(it)) } }.orEmpty()
        return FriendsSnap(o.optJSONObject("me")?.let(::card), fs, o.optInt("requests"), o.optLong("at"))
    }
}
