package com.myfit.tracker.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.GlucoseReading
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

/**
 * Family sharing for blood sugar (opt-in). The patient turns it on and gets a 6-character family code; a son or
 * daughter enters it in their MyFit app and can then see the readings and get an alert for very low / high values.
 *
 * Only while sharing is on, the last 14 days of readings (value, time-of-day tag, time) are copied to
 * glucose/{patientUid}/readings. Turning it off deletes that copy, the code and every carer's access.
 */
data class Carer(val uid: String, val name: String)
data class Patient(val uid: String, val name: String)
data class SharedReading(val id: String, val mgdl: Double, val tag: String, val takenAt: Long)

class GlucoseFamily(private val c: AppContainer) {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("glucose_family", Context.MODE_PRIVATE)

    fun available() = c.social.available && auth.currentUser != null
    fun sharingOn(ctx: Context) = prefs(ctx).getBoolean("on", false)
    fun myCode(ctx: Context): String? = prefs(ctx).getString("code", null)

    private fun newCode(): String = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")

    /** Turns sharing on: creates the family code and uploads recent readings. Returns the code. */
    suspend fun enable(ctx: Context): String {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first (You → Your MyFit account)")
        var code = newCode()
        repeat(5) { if (db.collection("glucoseShares").document(code).get().await().exists()) code = newCode() }
        val name = runCatching { c.profileRepo.profile.firstOrNull()?.name }.getOrNull()?.ifBlank { null } ?: u.displayName ?: "Family member"
        db.collection("glucoseShares").document(code).set(mapOf("uid" to u.uid, "name" to name.take(24))).await()
        db.collection("glucose").document(u.uid).set(mapOf("name" to name.take(24), "updatedAt" to FieldValue.serverTimestamp())).await()
        prefs(ctx).edit().putBoolean("on", true).putString("code", code).apply()
        upload(ctx)
        return code
    }

    /** Turns sharing off and removes everything that was shared. */
    suspend fun disable(ctx: Context) {
        val u = auth.currentUser
        val code = myCode(ctx)
        prefs(ctx).edit().putBoolean("on", false).remove("code").apply()
        if (u == null) return
        runCatching { code?.let { db.collection("glucoseShares").document(it).delete().await() } }
        val root = db.collection("glucose").document(u.uid)
        runCatching { root.collection("readings").get().await().documents.forEach { it.reference.delete().await() } }
        runCatching { root.collection("carers").get().await().documents.forEach { it.reference.delete().await() } }
        runCatching { root.delete().await() }
    }

    /** Copies the last 14 days of readings (only when sharing is on). Cheap to call after every new reading. */
    suspend fun upload(ctx: Context) {
        if (!sharingOn(ctx)) return
        val u = auth.currentUser ?: return
        val from = System.currentTimeMillis() - 14L * 86_400_000L
        val rs: List<GlucoseReading> = c.db.glucoseDao().between(from, System.currentTimeMillis() + 60_000L)
        val col = db.collection("glucose").document(u.uid).collection("readings")
        val batch = db.batch()
        rs.takeLast(400).forEach { r -> batch.set(col.document(r.uuid), mapOf("mgdl" to r.mgdl, "tag" to r.tag, "takenAt" to r.takenAt)) }
        batch.set(db.collection("glucose").document(u.uid), mapOf("updatedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
        batch.commit().await()
        // drop shared copies older than 14 days
        runCatching { col.whereLessThan("takenAt", from).get().await().documents.forEach { it.reference.delete().await() } }
    }

    suspend fun carers(): List<Carer> {
        val u = auth.currentUser ?: return emptyList()
        return db.collection("glucose").document(u.uid).collection("carers").get().await().documents.map { Carer(it.id, it.getString("name") ?: "Family") }
    }

    suspend fun removeCarer(uid: String) {
        val u = auth.currentUser ?: return
        db.collection("glucose").document(u.uid).collection("carers").document(uid).delete().await()
    }

    // ------------------------------------------------------------------ carer side

    /** Joins with a family code; afterwards the patient's readings appear under "People I care for". */
    suspend fun join(ctx: Context, code: String): Patient {
        val u = auth.currentUser ?: throw IllegalStateException("Sign in first (You → Your MyFit account)")
        val c2 = code.trim().uppercase()
        val share = db.collection("glucoseShares").document(c2).get().await()
        val owner = share.getString("uid") ?: throw IllegalArgumentException("No one has the family code $c2")
        if (owner == u.uid) throw IllegalArgumentException("That's your own code")
        val me = u.displayName ?: u.email?.substringBefore('@') ?: "Family"
        db.collection("glucose").document(owner).collection("carers").document(u.uid).set(mapOf("name" to me.take(24), "code" to c2)).await()
        val p = Patient(owner, share.getString("name") ?: "Family member")
        val set = prefs(ctx).getStringSet("patients", emptySet())!!.toMutableSet().apply { add("${p.uid}|${p.name}") }
        prefs(ctx).edit().putStringSet("patients", set).apply()
        GlucoseAlertWorker.schedule(ctx)
        return p
    }

    fun patients(ctx: Context): List<Patient> = prefs(ctx).getStringSet("patients", emptySet())!!.mapNotNull { s ->
        s.split('|', limit = 2).takeIf { it.size == 2 }?.let { Patient(it[0], it[1]) }
    }.sortedBy { it.name }

    suspend fun leave(ctx: Context, p: Patient) {
        val u = auth.currentUser
        if (u != null) runCatching { db.collection("glucose").document(p.uid).collection("carers").document(u.uid).delete().await() }
        val set = prefs(ctx).getStringSet("patients", emptySet())!!.filterNot { it.startsWith(p.uid + "|") }.toSet()
        prefs(ctx).edit().putStringSet("patients", set).apply()
    }

    suspend fun readings(p: Patient, limit: Long = 60): List<SharedReading> =
        db.collection("glucose").document(p.uid).collection("readings").orderBy("takenAt", Query.Direction.DESCENDING).limit(limit).get().await()
            .documents.mapNotNull { d ->
                val v = d.getDouble("mgdl") ?: return@mapNotNull null
                SharedReading(d.id, v, d.getString("tag") ?: "", d.getLong("takenAt") ?: 0L)
            }
}
