package com.myfit.tracker.ui.pip

import android.content.Context
import android.graphics.ImageDecoder
import androidx.annotation.RequiresApi
import com.myfit.tracker.ui.arena.Mascot
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream

/**
 * Home buddy: Pip by default, or any unlocked Arena character. Every character ships in the APK:
 * Pip has his full set (assets/pip), the others a sharp "lite" pack (assets/buddy/<id>: 9 key moves, talk
 * frames, portrait — no head-follow grid). Moves a character doesn't have play their closest kept move.
 * Everything that draws the buddy goes through [open]/[source] so it doesn't care which one it is.
 */
object Buddy {
    /** The buddy currently shown. */
    val active = MutableStateFlow(Mascot.PIP)
    @Volatile var lookN = 13; private set
    /** Only Pip has the look-at-your-finger head grid. */
    val hasLook: Boolean get() = active.value == Mascot.PIP

    /** Moves in every lite pack. */
    val LITE = setOf("idle", "wave", "celebrate", "thinking", "love", "sleepy", "letsgo", "train")
    private val FALLBACK = mapOf(
        "excited" to "celebrate", "dance" to "celebrate", "spin" to "celebrate", "cheer" to "celebrate", "clap" to "celebrate",
        "highfive" to "celebrate", "laugh" to "celebrate", "jumpingjacks" to "train", "squat" to "train", "stretch" to "train",
        "jog" to "train", "flex" to "letsgo", "salute" to "letsgo", "thumbsup" to "letsgo", "yes" to "letsgo", "point" to "letsgo",
        "hydrate" to "letsgo", "fuel" to "letsgo", "hearteyes" to "love", "blowkiss" to "love", "shy" to "love", "wink" to "love",
        "curious" to "thinking", "shrug" to "thinking", "surprised" to "thinking", "facepalm" to "thinking", "concerned" to "thinking",
        "sad" to "thinking", "grumpy" to "thinking", "pout" to "thinking", "dizzy" to "thinking", "no" to "thinking",
        "yawn" to "sleepy", "meditate" to "sleepy", "sneeze" to "sleepy", "bow" to "wave", "peekaboo" to "wave",
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    /** All characters are bundled — nothing to download. */
    @Suppress("UNUSED_PARAMETER") fun installed(c: Context, m: Mascot) = true
    val name: String get() = active.value.label.substringBefore(' ')

    /** How the AI should introduce itself. */
    fun persona(): String = active.value.let { m ->
        if (m == Mascot.PIP) "You are Pip, the cheerful little buddy inside \"MyFit Tracker\", a private fitness logbook app. You look like a soft mint plush with a navy striped sweatband, a curly antenna and little sneakers."
        else "You are ${m.label.substringBefore(' ')} (${m.label}), the user's chosen buddy inside \"MyFit Tracker\", a private fitness logbook app — a friendly 3D plush character from Pakistan's wildlife. Your motto: \"${m.tagline}\". Always call yourself ${m.label.substringBefore(' ')}, never Pip."
    }

    /** Call once at start-up. */
    fun init(c: Context) {
        // packs used to be downloaded (~15 MB each); they're bundled now, so free that space once
        java.io.File(c.applicationContext.filesDir, "buddy").takeIf { it.exists() }?.let { d -> Thread { d.deleteRecursively() }.start() }
        val id = prefs(c).getString("buddy", "pip")
        apply(Mascot.entries.firstOrNull { it.id == id } ?: Mascot.PIP)
    }

    private fun apply(m: Mascot) {
        lookN = if (m == Mascot.PIP) 13 else 1
        active.value = m
        onChange.forEach { it() }
    }

    internal val onChange = mutableListOf<() -> Unit>()

    fun choose(c: Context, m: Mascot) {
        prefs(c).edit().putString("buddy", m.id).apply()
        apply(m)
    }

    /** Asset path for a Pip-relative file ("idle.webp", "talk/0.webp", "look/look_06_06.webp") of the active buddy. */
    fun path(rel: String, m: Mascot = active.value): String {
        if (m == Mascot.PIP) return "pip/$rel"
        val base = "buddy/${m.id}/"
        return when {
            rel.startsWith("talk/") -> base + rel
            rel.startsWith("look/") -> base + "portrait.webp"
            else -> {
                val clip = rel.removeSuffix(".webp")
                base + (if (clip in LITE) clip else FALLBACK[clip] ?: "idle") + ".webp"
            }
        }
    }

    fun open(c: Context, rel: String): InputStream = c.assets.open(path(rel))

    @RequiresApi(28)
    fun source(c: Context, rel: String): ImageDecoder.Source = ImageDecoder.createSource(c.assets, path(rel))
}
