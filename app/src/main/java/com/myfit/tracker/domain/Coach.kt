package com.myfit.tracker.domain

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pro trainer & nutritionist personas. The characters are fictional (names are made up and editable),
 * drawn as realistic 3D renders in assets/coach. Everything is stored on this phone.
 */
object Coach {
    /** Trainer looks. Art files: assets/coach/<id>_<pose>.webp (pose = portrait, stand, demo, cheer). */
    enum class Look(val id: String, val male: Boolean, val label: String, val defaultName: String) {
        M("m", true, "Male coach", "Coach Zarak"),
        F("f", false, "Female coach", "Coach Inaya"),
        FH("fh", false, "Female coach (hijab)", "Coach Inaya"),
    }

    /** Nutritionist looks. Art: assets/coach/<id>_<pose>.webp (pose = portrait, plate). */
    enum class NLook(val id: String, val male: Boolean, val label: String, val defaultName: String) {
        F("nf", false, "Female nutritionist", "Areesha"),
        M("nm", true, "Male nutritionist", "Faraz"),
    }

    enum class Push(val label: String) { CALM("Calm"), STEADY("Steady"), HARD("Push me hard") }

    data class Prefs(
        val look: Look = Look.M,
        val name: String = Look.M.defaultName,
        val nLook: NLook = NLook.F,
        val nName: String = NLook.F.defaultName,
        val urdu: Boolean = false,
        val camera: Boolean = false,
        val voice: Boolean = true,
        val push: Push = Push.STEADY,
        val frontCamera: Boolean = true,
    )

    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> = _prefs
    @Volatile private var loaded = false

    private fun sp(c: Context) = c.getSharedPreferences("coach", Context.MODE_PRIVATE)

    fun load(c: Context): Prefs {
        if (loaded) return _prefs.value
        val p = sp(c)
        val look = Look.entries.firstOrNull { it.id == p.getString("look", null) } ?: Look.M
        val nLook = NLook.entries.firstOrNull { it.id == p.getString("nlook", null) } ?: NLook.F
        _prefs.value = Prefs(
            look, p.getString("name", null)?.takeIf { it.isNotBlank() } ?: look.defaultName,
            nLook, p.getString("nname", null)?.takeIf { it.isNotBlank() } ?: nLook.defaultName,
            p.getBoolean("urdu", false), p.getBoolean("camera", false), p.getBoolean("voice", true),
            Push.entries.getOrNull(p.getInt("push", 1)) ?: Push.STEADY, p.getBoolean("front", true),
        )
        loaded = true
        return _prefs.value
    }

    fun update(c: Context, f: (Prefs) -> Prefs) {
        load(c)
        val old = _prefs.value
        var n = f(old)
        // switching look keeps a custom name, but swaps a default one
        if (n.look != old.look && old.name == old.look.defaultName) n = n.copy(name = n.look.defaultName)
        if (n.nLook != old.nLook && old.nName == old.nLook.defaultName) n = n.copy(nName = n.nLook.defaultName)
        n = n.copy(name = n.name.trim().take(24).ifBlank { n.look.defaultName }, nName = n.nName.trim().take(24).ifBlank { n.nLook.defaultName })
        _prefs.value = n
        sp(c).edit().putString("look", n.look.id).putString("name", n.name).putString("nlook", n.nLook.id).putString("nname", n.nName)
            .putBoolean("urdu", n.urdu).putBoolean("camera", n.camera).putBoolean("voice", n.voice).putInt("push", n.push.ordinal)
            .putBoolean("front", n.frontCamera).apply()
    }

    /** Asset path of a pose, or null when the art isn't bundled. */
    fun art(c: Context, look: String, pose: String): String? {
        val f = "${look}_$pose.webp"
        return if (artFiles(c).contains(f)) "coach/$f" else null
    }

    @Volatile private var files: Set<String>? = null
    private fun artFiles(c: Context): Set<String> = files ?: (runCatching { c.assets.list("coach")?.toSet() }.getOrNull() ?: emptySet()).also { files = it }
}
