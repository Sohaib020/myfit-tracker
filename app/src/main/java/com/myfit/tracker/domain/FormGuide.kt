package com.myfit.tracker.domain

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** One line the coach says/shows, in English and Urdu. */
data class Say(val en: String, val ur: String) {
    fun text(urdu: Boolean) = if (urdu) ur else en
}

/**
 * Form coaching for the pro trainer: movement pattern per exercise, rep tempo, setup and angle cues,
 * and (for patterns the camera can follow) on-device rep counting with form checks from body landmarks.
 * Joint angles are guidance for typical healthy adults, not medical advice.
 */
object FormGuide {
    enum class Pattern { SQUAT, HINGE, LUNGE, PUSHUP, BENCH, OVERHEAD, ROW, PULLUP, CURL, TRICEP, RAISE, PLANK, CRUNCH, CARDIO, OTHER }

    /** A tempo phase: what to do and for how long (seconds). */
    data class Phase(val word: Say, val sec: Float)

    fun of(name: String, primaryMuscle: String = "", measurement: String = ""): Pattern {
        val n = name.lowercase()
        fun has(vararg k: String) = k.any { it in n }
        return when {
            has("plank", "hollow hold", "wall sit", "dead hang") -> Pattern.PLANK
            has("run", "jog", "walk", "cycl", "bike", "rowing machine", "elliptical", "skipping", "jump rope", "stair", "treadmill", "burpee", "jumping jack", "high knee", "mountain climber") -> Pattern.CARDIO
            has("lunge", "split squat", "step up", "step-up") -> Pattern.LUNGE
            has("squat", "leg press", "hack") -> Pattern.SQUAT
            has("deadlift", "romanian", "rdl", "good morning", "hip thrust", "glute bridge", "kettlebell swing", "hyperextension", "back extension") -> Pattern.HINGE
            has("push-up", "push up", "pushup", "press-up", "dip") -> Pattern.PUSHUP
            has("bench", "chest press", "fly", "flye", "pec deck", "floor press") -> Pattern.BENCH
            has("overhead", "shoulder press", "military", "arnold", "push press", "pike") -> Pattern.OVERHEAD
            has("pull-up", "pull up", "pullup", "chin", "pulldown", "pull-down") -> Pattern.PULLUP
            has("row", "face pull", "shrug") -> Pattern.ROW
            has("curl") && !has("leg curl", "hamstring curl") -> Pattern.CURL
            has("tricep", "triceps", "pushdown", "skull", "kickback", "extension") && !has("leg extension", "back extension") -> Pattern.TRICEP
            has("lateral raise", "front raise", "side raise", "rear delt", "reverse fly", "upright row", "y raise") -> Pattern.RAISE
            has("crunch", "sit-up", "sit up", "leg raise", "russian twist", "v-up", "bicycle", "ab ", "abs", "toe touch") -> Pattern.CRUNCH
            measurement.contains("DURATION") || measurement.contains("DISTANCE") -> Pattern.CARDIO
            primaryMuscle.equals("QUADS", true) || primaryMuscle.equals("LEGS", true) || primaryMuscle.equals("GLUTES", true) -> Pattern.SQUAT
            primaryMuscle.equals("BICEPS", true) -> Pattern.CURL
            primaryMuscle.equals("TRICEPS", true) -> Pattern.TRICEP
            primaryMuscle.equals("CHEST", true) -> Pattern.BENCH
            primaryMuscle.equals("BACK", true) || primaryMuscle.equals("LATS", true) -> Pattern.ROW
            primaryMuscle.equals("SHOULDERS", true) -> Pattern.OVERHEAD
            primaryMuscle.equals("ABS", true) || primaryMuscle.equals("CORE", true) -> Pattern.CRUNCH
            else -> Pattern.OTHER
        }
    }

    // ------------------------------------------------------------------ words
    val DOWN = Say("Down", "نیچے"); val UP = Say("Up", "اوپر"); val PUSH = Say("Push", "دھکیلیں"); val PULL = Say("Pull", "کھینچیں")
    val HOLD = Say("Hold", "روکیں"); val SQUEEZE = Say("Squeeze", "دبائیں"); val LOWER = Say("Lower", "نیچے لائیں"); val BREATHE = Say("Breathe", "سانس")

    /** Controlled default tempo: about 2 s lowering, 1 s lifting, short pause. */
    fun tempo(p: Pattern): List<Phase> = when (p) {
        Pattern.SQUAT, Pattern.LUNGE, Pattern.HINGE -> listOf(Phase(DOWN, 2f), Phase(UP, 1.2f), Phase(BREATHE, 0.6f))
        Pattern.PUSHUP, Pattern.BENCH -> listOf(Phase(DOWN, 2f), Phase(PUSH, 1f), Phase(BREATHE, 0.5f))
        Pattern.OVERHEAD -> listOf(Phase(PUSH, 1f), Phase(LOWER, 2f), Phase(BREATHE, 0.5f))
        Pattern.ROW, Pattern.PULLUP -> listOf(Phase(PULL, 1f), Phase(SQUEEZE, 0.6f), Phase(LOWER, 2f))
        Pattern.CURL, Pattern.TRICEP -> listOf(Phase(UP, 1f), Phase(SQUEEZE, 0.6f), Phase(DOWN, 2f))
        Pattern.RAISE -> listOf(Phase(UP, 1.2f), Phase(HOLD, 0.5f), Phase(DOWN, 2f))
        Pattern.CRUNCH -> listOf(Phase(UP, 1f), Phase(SQUEEZE, 0.5f), Phase(DOWN, 1.5f))
        Pattern.PLANK, Pattern.CARDIO -> emptyList()
        Pattern.OTHER -> listOf(Phase(UP, 1.2f), Phase(DOWN, 2f), Phase(BREATHE, 0.4f))
    }

    /** Setup + form cues with joint angles, said before and during the set. */
    fun cues(p: Pattern): List<Say> = when (p) {
        Pattern.SQUAT -> listOf(
            Say("Feet shoulder-width apart, toes slightly out.", "پاؤں کندھوں جتنے کھولیں، پنجے تھوڑے باہر۔"),
            Say("Brace your core and keep your chest up.", "پیٹ ٹائٹ رکھیں اور سینہ اوپر۔"),
            Say("Sit back and down until your thighs are about parallel, around 90 degrees at the knee.", "پیچھے بیٹھیں، اتنا نیچے کہ رانیں زمین کے متوازی ہوں، گھٹنے پر تقریباً 90 ڈگری۔"),
            Say("Knees follow your toes, push through the whole foot.", "گھٹنے پنجوں کی سیدھ میں، پورے پاؤں سے زور لگائیں۔"))
        Pattern.HINGE -> listOf(
            Say("Weight close to your legs, back flat.", "وزن ٹانگوں کے قریب، کمر سیدھی۔"),
            Say("Push your hips back with soft knees.", "کولہے پیچھے دھکیلیں، گھٹنے ہلکے سے مڑے۔"),
            Say("Lower until your back is about 45 degrees or you feel your hamstrings stretch.", "اتنا جھکیں کہ کمر تقریباً 45 ڈگری پر ہو یا ران کے پیچھے کھنچاؤ محسوس ہو۔"),
            Say("Squeeze your glutes to stand tall.", "کھڑے ہوتے وقت کولہے ٹائٹ کریں۔"))
        Pattern.LUNGE -> listOf(
            Say("Big step, torso tall.", "بڑا قدم، جسم سیدھا۔"),
            Say("Both knees bend to about 90 degrees, back knee just above the floor.", "دونوں گھٹنے تقریباً 90 ڈگری، پچھلا گھٹنا زمین کے بالکل قریب۔"),
            Say("Drive up through your front heel.", "اگلی ایڑی سے زور لگا کر اوپر آئیں۔"))
        Pattern.PUSHUP -> listOf(
            Say("Hands just wider than your shoulders, body in one straight line.", "ہاتھ کندھوں سے تھوڑے کھلے، جسم ایک سیدھی لائن میں۔"),
            Say("Elbows about 45 degrees from your body, not flared out.", "کہنیاں جسم سے تقریباً 45 ڈگری پر، باہر نہ پھیلائیں۔"),
            Say("Lower until your elbows reach 90 degrees or your chest is just above the floor.", "اتنا نیچے جائیں کہ کہنیاں 90 ڈگری پر ہوں یا سینہ زمین کے قریب۔"))
        Pattern.BENCH -> listOf(
            Say("Shoulder blades pulled back and down, feet flat.", "کندھے پیچھے اور نیچے دبائیں، پاؤں زمین پر۔"),
            Say("Lower to mid-chest with elbows around 45 to 60 degrees.", "وزن سینے کے درمیان تک لائیں، کہنیاں 45 سے 60 ڈگری پر۔"),
            Say("Press up smoothly, no bouncing.", "آرام سے اوپر دھکیلیں، اچھالیں نہیں۔"))
        Pattern.OVERHEAD -> listOf(
            Say("Ribs down, glutes tight, don't lean back.", "پسلیاں نیچے، کولہے ٹائٹ، پیچھے مت جھکیں۔"),
            Say("Start with elbows just under your wrists, about 90 degrees.", "شروع میں کہنیاں کلائیوں کے نیچے، تقریباً 90 ڈگری۔"),
            Say("Press straight up until your arms lock out by your ears.", "سیدھا اوپر دھکیلیں جب تک بازو کانوں کے پاس پورے سیدھے نہ ہوں۔"))
        Pattern.ROW -> listOf(
            Say("Hinge forward with a flat back, about 45 degrees.", "کمر سیدھی رکھ کر تقریباً 45 ڈگری آگے جھکیں۔"),
            Say("Pull your elbows back towards your hips and squeeze your shoulder blades.", "کہنیاں کولہوں کی طرف کھینچیں اور کندھے آپس میں دبائیں۔"),
            Say("Lower slowly until your arms are straight.", "آہستہ نیچے لائیں جب تک بازو سیدھے نہ ہوں۔"))
        Pattern.PULLUP -> listOf(
            Say("Grip a little wider than your shoulders, chest up.", "گرپ کندھوں سے تھوڑی چوڑی، سینہ اوپر۔"),
            Say("Drive your elbows down to your ribs.", "کہنیاں نیچے پسلیوں کی طرف کھینچیں۔"),
            Say("Control the way back to a full stretch.", "واپسی پر کنٹرول سے پورا کھنچاؤ۔"))
        Pattern.CURL -> listOf(
            Say("Elbows pinned to your sides.", "کہنیاں جسم کے ساتھ جڑی رہیں۔"),
            Say("Curl up without swinging, squeeze at the top.", "بغیر جھولے اوپر لائیں، اوپر جا کر دبائیں۔"),
            Say("Lower all the way until your arms are nearly straight.", "پورا نیچے لائیں جب تک بازو تقریباً سیدھے نہ ہوں۔"))
        Pattern.TRICEP -> listOf(
            Say("Upper arms stay still, only your forearms move.", "اوپری بازو ساکن، صرف نچلا حصہ حرکت کرے۔"),
            Say("Straighten fully and squeeze the back of your arms.", "بازو پورا سیدھا کریں اور پچھلا حصہ دبائیں۔"))
        Pattern.RAISE -> listOf(
            Say("Slight bend in your elbows, shoulders down.", "کہنیوں میں ہلکا سا خم، کندھے نیچے۔"),
            Say("Raise to shoulder height, about 90 degrees, not higher.", "کندھوں کی اونچائی تک، تقریباً 90 ڈگری، اس سے زیادہ نہیں۔"),
            Say("Lower slowly, no swinging.", "آہستہ نیچے لائیں، جھولا نہیں۔"))
        Pattern.PLANK -> listOf(
            Say("Elbows under your shoulders, body straight from head to heels.", "کہنیاں کندھوں کے نیچے، سر سے ایڑی تک جسم سیدھا۔"),
            Say("Squeeze your glutes and brace your stomach.", "کولہے اور پیٹ ٹائٹ رکھیں۔"),
            Say("Keep breathing.", "سانس لیتے رہیں۔"))
        Pattern.CRUNCH -> listOf(
            Say("Lower back pressed down, chin off your chest.", "کمر کا نچلا حصہ زمین سے لگا رہے، ٹھوڑی سینے سے دور۔"),
            Say("Breathe out as you curl up.", "اوپر آتے ہوئے سانس باہر نکالیں۔"))
        Pattern.CARDIO -> listOf(
            Say("Find a pace you can keep. You should still be able to talk.", "ایسی رفتار رکھیں کہ آپ بات کر سکیں۔"),
            Say("Relax your shoulders and breathe steadily.", "کندھے ڈھیلے، سانس برابر۔"))
        Pattern.OTHER -> listOf(
            Say("Move slowly and with control.", "آہستہ اور کنٹرول سے حرکت کریں۔"),
            Say("Use the full range and breathe out on the effort.", "پوری رینج استعمال کریں، زور لگاتے وقت سانس باہر۔"))
    }

    // ------------------------------------------------------------------ coach lines
    object Lines {
        private val UR_NUM = listOf("", "ایک", "دو", "تین", "چار", "پانچ", "چھ", "سات", "آٹھ", "نو", "دس", "گیارہ", "بارہ", "تیرہ", "چودہ", "پندرہ",
            "سولہ", "سترہ", "اٹھارہ", "انیس", "بیس", "اکیس", "بائیس", "تئیس", "چوبیس", "پچیس", "چھبیس", "ستائیس", "اٹھائیس", "انتیس", "تیس")
        fun count(n: Int) = Say("$n", UR_NUM.getOrNull(n) ?: "$n")
        fun intro(ex: String, set: Int, target: Say?) = Say(
            "$ex. Set $set" + (target?.let { ", ${it.en}" } ?: "") + ".",
            "$ex۔ سیٹ نمبر $set" + (target?.let { "، ${it.ur}" } ?: "") + "۔")
        fun target(reps: Int?, sec: Long?, weight: String?): Say? {
            val parts = mutableListOf<Say>()
            reps?.let { parts += Say("$it reps", "$it ریپس") }
            sec?.let { parts += Say("$it seconds", "$it سیکنڈ") }
            weight?.let { parts += Say("at $it", "$it کے ساتھ") }
            if (parts.isEmpty()) return null
            return Say(parts.joinToString(" ") { it.en }, parts.joinToString(" ") { it.ur })
        }
        val COUNTDOWN = Say("Three, two, one, go!", "تین، دو، ایک، شروع!")
        val READY = Say("Get into position.", "پوزیشن میں آ جائیں۔")
        fun more(n: Int) = Say("$n more!", "بس $n اور!")
        val LAST = Say("Last one, make it count!", "آخری ایک، پوری طاقت سے!")
        val SLOWING = Say("Don't stop now, push!", "ابھی مت رکیں، زور لگائیں!")
        fun setDone(reps: Int?) = Say("Set done" + (reps?.let { ", $it reps" } ?: "") + ". Great work!", "سیٹ مکمل" + (reps?.let { "، $it ریپس" } ?: "") + "۔ بہت خوب!")
        fun rest(sec: Int) = Say("Rest $sec seconds. Shake it out and breathe.", "$sec سیکنڈ آرام کریں۔ سانس بحال کریں۔")
        val REST_10 = Say("Ten seconds. Get ready.", "دس سیکنڈ۔ تیار ہو جائیں۔")
        val HALF = Say("Halfway there, hold strong.", "آدھا ہو گیا، مضبوطی سے رکے رہیں۔")
        val TEN_LEFT = Say("Ten seconds left!", "دس سیکنڈ باقی!")
        val TIME = Say("Time! Well done.", "وقت پورا! بہت خوب۔")
        val WORKOUT_DONE = Say("That's the workout. Proud of you!", "ورزش مکمل۔ مجھے آپ پر فخر ہے!")
        val CAM_START = Say("I'll count your reps and check your form. Turn side-on to the camera.", "میں آپ کے ریپس گنوں گا اور فارم چیک کروں گا۔ کیمرے کی طرف سائیڈ سے کھڑے ہوں۔")
        val CAM_START_F = Say("I'll count your reps and check your form. Turn side-on to the camera.", "میں آپ کے ریپس گنوں گی اور فارم چیک کروں گی۔ کیمرے کی طرف سائیڈ سے کھڑی ہوں۔")
        private val CHEER = listOf(
            Say("Strong! Keep going.", "زبردست! جاری رکھیں۔"), Say("That's it, nice and controlled.", "بالکل ایسے ہی، کنٹرول کے ساتھ۔"),
            Say("You've got this.", "آپ کر سکتے ہیں۔"), Say("Breathe, and drive!", "سانس لیں اور زور لگائیں!"), Say("Looking good!", "بہت اچھے جا رہے ہیں!"))
        private val HARD = listOf(Say("Dig deep!", "پوری جان لگا دیں!"), Say("Come on, more power!", "چلیں، اور زور!"), Say("No quitting now!", "اب ہار نہیں ماننی!"))
        fun cheer(i: Int, hard: Boolean) = if (hard && i % 2 == 1) HARD[(i / 2) % HARD.size] else CHEER[i % CHEER.size]
    }

    // ------------------------------------------------------------------ camera
    /** Things the camera can tell you, mid-set. */
    enum class Fix(val say: Say) {
        STEP_BACK(Say("Step back so I can see your whole body.", "تھوڑا پیچھے ہٹیں تاکہ پورا جسم نظر آئے۔")),
        CHEST_UP(Say("Chest up!", "سینہ اوپر!")),
        DEEPER(Say("A little deeper.", "تھوڑا اور نیچے۔")),
        HIPS_UP(Say("Lift your hips a little.", "کولہے تھوڑے اوپر کریں۔")),
        HIPS_DOWN(Say("Drop your hips in line.", "کولہے نیچے، سیدھ میں۔")),
        ELBOWS(Say("Keep your elbows at your sides.", "کہنیاں ساتھ رکھیں۔")),
        FULL(Say("Full range, all the way.", "پوری رینج، مکمل۔")),
        LOCKOUT(Say("Lock it out at the top.", "اوپر جا کر بازو پورے سیدھے کریں۔")),
        TOO_HIGH(Say("Stop at shoulder height.", "کندھے کی اونچائی پر رکیں۔")),
        STAY_TALL(Say("Stay tall.", "سیدھے رہیں۔")),
        SLOW(Say("Slow down, control it.", "آہستہ، کنٹرول کے ساتھ۔")),
    }

    /** Rep rule: a joint angle (landmark indices a-b-c) travels from a start zone to a target zone and back. */
    data class Rule(val a: Int, val b: Int, val c: Int, val startHigh: Boolean, val start: Float, val target: Float, val partial: Float, val partialFix: Fix)

    // ML Kit landmark indices (left side; right = +1)
    private const val SH = 11; private const val EL = 13; private const val WR = 15; private const val HIP = 23; private const val KN = 25; private const val AN = 27

    fun rule(p: Pattern): Rule? = when (p) {
        Pattern.SQUAT -> Rule(HIP, KN, AN, true, 160f, 105f, 130f, Fix.DEEPER)
        Pattern.LUNGE -> Rule(HIP, KN, AN, true, 160f, 110f, 135f, Fix.DEEPER)
        Pattern.HINGE -> Rule(SH, HIP, KN, true, 160f, 125f, 145f, Fix.DEEPER)
        Pattern.PUSHUP -> Rule(SH, EL, WR, true, 150f, 100f, 125f, Fix.DEEPER)
        Pattern.CURL -> Rule(SH, EL, WR, true, 140f, 65f, 95f, Fix.FULL)
        Pattern.OVERHEAD -> Rule(SH, EL, WR, false, 105f, 155f, 135f, Fix.LOCKOUT)
        Pattern.TRICEP -> Rule(SH, EL, WR, false, 100f, 150f, 130f, Fix.LOCKOUT)
        Pattern.RAISE -> Rule(HIP, SH, EL, false, 30f, 70f, 50f, Fix.FULL)
        Pattern.ROW -> Rule(SH, EL, WR, true, 145f, 100f, 120f, Fix.FULL)
        else -> null
    }
    fun cameraCounts(p: Pattern) = rule(p) != null
    fun cameraHolds(p: Pattern) = p == Pattern.PLANK

    /** 33 landmarks as x,y,likelihood triples in upright image pixels. */
    class Pose(val pts: FloatArray) {
        fun x(i: Int) = pts[i * 3]; fun y(i: Int) = pts[i * 3 + 1]; fun ok(i: Int) = pts[i * 3 + 2] > 0.5f
        fun angle(a: Int, b: Int, c: Int): Float {
            val v1x = x(a) - x(b); val v1y = y(a) - y(b); val v2x = x(c) - x(b); val v2y = y(c) - y(b)
            val d = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
            if (d < 1e-3f) return 180f
            return Math.toDegrees(acos(((v1x * v2x + v1y * v2y) / d).coerceIn(-1f, 1f).toDouble())).toFloat()
        }
        /** Lean of the segment a→b from vertical, degrees. */
        fun lean(a: Int, b: Int) = abs(Math.toDegrees(atan2((x(b) - x(a)).toDouble(), (y(a) - y(b)).toDouble()))).toFloat().let { if (it > 90) 180 - it else it }
        fun side(vararg idx: Int): Int {
            val l = idx.sumOf { pts[it * 3 + 2].toDouble() }; val r = idx.sumOf { pts[(it + 1) * 3 + 2].toDouble() }
            return if (r > l) 1 else 0
        }
    }

    /**
     * Counts reps from a stream of poses and spots form slips. Feed every frame to [feed];
     * it returns events to show/say. Thresholds are generous so people with less mobility still get counted.
     */
    class Counter(private val pattern: Pattern) {
        sealed interface Event { data class Rep(val n: Int, val sec: Float) : Event; data class Cue(val fix: FormGuide.Fix) : Event; data class Angle(val deg: Float) : Event }
        private val r = rule(pattern)
        var reps = 0; private set
        private var moving = false
        private var extreme = 0f
        private var repStart = 0L
        private val times = ArrayList<Float>()
        private var lastFix = 0L
        private var formBad = 0
        private var unseen = 0

        fun feed(p: Pose, now: Long): List<Event> {
            val out = mutableListOf<Event>()
            if (pattern == Pattern.PLANK) return plank(p, now)
            val rr = r ?: return out
            val s = p.side(rr.a, rr.b, rr.c)
            val a = rr.a + s; val b = rr.b + s; val c = rr.c + s
            if (!(p.ok(a) && p.ok(b) && p.ok(c))) {
                if (++unseen == 25) fix(out, Fix.STEP_BACK, now, force = true)
                return out
            }
            unseen = 0
            val ang = p.angle(a, b, c)
            out += Event.Angle(ang)
            val inStart = if (rr.startHigh) ang >= rr.start else ang <= rr.start
            val past = { v: Float, t: Float -> if (rr.startHigh) v <= t else v >= t }
            if (!moving) {
                if (!inStart && past(ang, (rr.start + rr.partial) / 2f)) { moving = true; extreme = ang; repStart = now }
            } else {
                extreme = if (rr.startHigh) minOf(extreme, ang) else maxOf(extreme, ang)
                formChecks(p, s, ang, now, out)
                if (inStart) {
                    moving = false
                    if (past(extreme, rr.target)) {
                        reps++
                        val sec = (now - repStart) / 1000f
                        times += sec
                        out += Event.Rep(reps, sec)
                        if (sec < 0.7f) fix(out, Fix.SLOW, now)
                    } else if (past(extreme, rr.partial)) fix(out, rr.partialFix, now, force = true)
                }
            }
            return out
        }

        /** True when the last rep took much longer than the first ones — time to push. */
        fun slowing(): Boolean = times.size >= 4 && times.last() > 1.6f * times.take(3).average()

        private fun formChecks(p: Pose, s: Int, ang: Float, now: Long, out: MutableList<Event>) {
            val sh = SH + s; val hip = HIP + s; val kn = KN + s; val an = AN + s; val el = EL + s
            when (pattern) {
                Pattern.SQUAT -> if (p.ok(sh) && p.ok(hip) && p.lean(hip, sh) > 55f) bad(out, Fix.CHEST_UP, now)
                Pattern.LUNGE -> if (p.ok(sh) && p.ok(hip) && p.lean(hip, sh) > 30f) bad(out, Fix.STAY_TALL, now)
                Pattern.PUSHUP -> if (p.ok(sh) && p.ok(hip) && p.ok(an)) {
                    if (p.angle(sh, hip, an) < 150f) bad(out, if (hipAbove(p, sh, hip, an)) Fix.HIPS_DOWN else Fix.HIPS_UP, now)
                }
                Pattern.CURL -> if (p.ok(sh) && p.ok(el) && p.ok(hip) && p.angle(hip, sh, el) > 35f) bad(out, Fix.ELBOWS, now)
                Pattern.RAISE -> if (ang > 110f) bad(out, Fix.TOO_HIGH, now)
                else -> {}
            }
        }

        private var holdBadSince = 0L
        private fun plank(p: Pose, now: Long): List<Event> {
            val out = mutableListOf<Event>()
            val s = p.side(SH, HIP, AN); val sh = SH + s; val hip = HIP + s; val an = AN + s
            if (!(p.ok(sh) && p.ok(hip) && p.ok(an))) { if (++unseen == 25) fix(out, Fix.STEP_BACK, now, force = true); return out }
            unseen = 0
            val line = p.angle(sh, hip, an)
            out += Event.Angle(line)
            if (line < 160f) {
                if (holdBadSince == 0L) holdBadSince = now
                if (now - holdBadSince > 1200) { fix(out, if (hipAbove(p, sh, hip, an)) Fix.HIPS_DOWN else Fix.HIPS_UP, now); holdBadSince = now }
            } else holdBadSince = 0L
            return out
        }

        /** Image y grows downward: hips "above" the shoulder-ankle line means piked. */
        private fun hipAbove(p: Pose, sh: Int, hip: Int, an: Int): Boolean {
            val t = if (abs(p.x(an) - p.x(sh)) < 1f) 0.5f else (p.x(hip) - p.x(sh)) / (p.x(an) - p.x(sh))
            val lineY = p.y(sh) + t * (p.y(an) - p.y(sh))
            return p.y(hip) < lineY
        }

        private fun bad(out: MutableList<Event>, f: Fix, now: Long) { if (++formBad >= 4) { fix(out, f, now); formBad = 0 } }
        private fun fix(out: MutableList<Event>, f: Fix, now: Long, force: Boolean = false) {
            if (!force && now - lastFix < 3500) return
            if (force && now - lastFix < 1500) return
            lastFix = now; out += Event.Cue(f)
        }
    }
}
