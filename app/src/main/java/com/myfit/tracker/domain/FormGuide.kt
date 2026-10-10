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
    enum class Pattern { SQUAT, HINGE, LUNGE, PUSHUP, BENCH, OVERHEAD, ROW, PULLUP, CURL, TRICEP, RAISE, PLANK, CRUNCH, CARDIO, OTHER,
        JUMPING_JACK, HIGH_KNEES, GLUTE_BRIDGE, SITUP, MOUNTAIN_CLIMBER, WALL_SIT, SIDE_PLANK, CALF_RAISE, BURPEE }

    /** A tempo phase: what to do and for how long (seconds). */
    data class Phase(val word: Say, val sec: Float)

    fun of(name: String, primaryMuscle: String = "", measurement: String = ""): Pattern {
        val n = name.lowercase()
        fun has(vararg k: String) = k.any { it in n }
        return when {
            has("side plank") -> Pattern.SIDE_PLANK
            has("wall sit") -> Pattern.WALL_SIT
            has("plank", "hollow hold", "dead hang") -> Pattern.PLANK
            has("jumping jack", "star jump") -> Pattern.JUMPING_JACK
            has("high knee") -> Pattern.HIGH_KNEES
            has("mountain climber") -> Pattern.MOUNTAIN_CLIMBER
            has("burpee") -> Pattern.BURPEE
            has("calf raise", "heel raise") -> Pattern.CALF_RAISE
            has("glute bridge", "hip bridge", "hip thrust") -> Pattern.GLUTE_BRIDGE
            has("sit-up", "sit up", "situp") -> Pattern.SITUP
            has("run", "jog", "walk", "cycl", "bike", "rowing machine", "elliptical", "skipping", "jump rope", "stair", "treadmill") -> Pattern.CARDIO
            has("lunge", "split squat", "step up", "step-up") -> Pattern.LUNGE
            has("squat", "leg press", "hack") -> Pattern.SQUAT
            has("deadlift", "romanian", "rdl", "good morning", "kettlebell swing", "hyperextension", "back extension") -> Pattern.HINGE
            has("push-up", "push up", "pushup", "press-up", "dip") -> Pattern.PUSHUP
            has("bench", "chest press", "fly", "flye", "pec deck", "floor press") -> Pattern.BENCH
            has("overhead", "shoulder press", "military", "arnold", "push press", "pike") -> Pattern.OVERHEAD
            has("pull-up", "pull up", "pullup", "chin", "pulldown", "pull-down") -> Pattern.PULLUP
            has("row", "face pull", "shrug") -> Pattern.ROW
            has("curl") && !has("leg curl", "hamstring curl") -> Pattern.CURL
            has("tricep", "triceps", "pushdown", "skull", "kickback", "extension") && !has("leg extension", "back extension") -> Pattern.TRICEP
            has("lateral raise", "front raise", "side raise", "rear delt", "reverse fly", "upright row", "y raise") -> Pattern.RAISE
            has("crunch", "leg raise", "russian twist", "v-up", "bicycle", "ab ", "abs", "toe touch") -> Pattern.CRUNCH
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
        Pattern.GLUTE_BRIDGE, Pattern.CALF_RAISE -> listOf(Phase(UP, 1f), Phase(SQUEEZE, 1f), Phase(DOWN, 1.5f))
        Pattern.SITUP -> listOf(Phase(UP, 1.2f), Phase(DOWN, 1.8f))
        Pattern.JUMPING_JACK, Pattern.HIGH_KNEES, Pattern.MOUNTAIN_CLIMBER, Pattern.BURPEE, Pattern.WALL_SIT, Pattern.SIDE_PLANK -> emptyList()
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
        Pattern.JUMPING_JACK -> listOf(
            Say("Jump your feet wide and swing your arms all the way overhead.", "پاؤں کھول کر چھلانگ لگائیں اور بازو پورے سر کے اوپر لے جائیں۔"),
            Say("Land softly on the balls of your feet.", "پنجوں پر نرمی سے اتریں۔"))
        Pattern.HIGH_KNEES -> listOf(
            Say("Drive each knee up to hip height, about 90 degrees.", "ہر گھٹنا کولہے کی اونچائی تک، تقریباً 90 ڈگری۔"),
            Say("Stay tall and pump your arms.", "جسم سیدھا رکھیں اور بازو چلائیں۔"))
        Pattern.GLUTE_BRIDGE -> listOf(
            Say("Feet flat, knees bent, arms by your sides.", "پاؤں زمین پر، گھٹنے مڑے، بازو ساتھ۔"),
            Say("Push through your heels until your body makes a straight line from shoulders to knees.", "ایڑیوں سے زور لگائیں جب تک کندھوں سے گھٹنوں تک جسم سیدھا نہ ہو جائے۔"),
            Say("Squeeze your glutes at the top.", "اوپر جا کر کولہے دبائیں۔"))
        Pattern.SITUP -> listOf(
            Say("Knees bent, feet flat, hands across your chest.", "گھٹنے مڑے، پاؤں زمین پر، ہاتھ سینے پر۔"),
            Say("Curl up until your chest is close to your knees, then lower slowly.", "اوپر آئیں جب تک سینہ گھٹنوں کے قریب نہ ہو، پھر آہستہ نیچے جائیں۔"))
        Pattern.MOUNTAIN_CLIMBER -> listOf(
            Say("Start in a high plank, hands under shoulders.", "ہائی پلانک میں، ہاتھ کندھوں کے نیچے۔"),
            Say("Drive your knees towards your chest one at a time, hips low.", "گھٹنے باری باری سینے کی طرف لائیں، کولہے نیچے۔"))
        Pattern.WALL_SIT -> listOf(
            Say("Back flat on the wall, slide down until your knees are at 90 degrees.", "کمر دیوار سے لگا کر نیچے آئیں جب تک گھٹنے 90 ڈگری پر نہ ہوں۔"),
            Say("Knees over ankles, keep breathing.", "گھٹنے ٹخنوں کے اوپر، سانس لیتے رہیں۔"))
        Pattern.SIDE_PLANK -> listOf(
            Say("Elbow under your shoulder, body in one straight line.", "کہنی کندھے کے نیچے، جسم ایک سیدھی لائن میں۔"),
            Say("Lift your hips and don't let them sag.", "کولہے اوپر رکھیں، نیچے نہ گرنے دیں۔"))
        Pattern.CALF_RAISE -> listOf(
            Say("Rise up high onto the balls of your feet.", "پنجوں کے بل اونچا اٹھیں۔"),
            Say("Pause at the top, then lower all the way down.", "اوپر رکیں، پھر پورا نیچے لائیں۔"))
        Pattern.BURPEE -> listOf(
            Say("Squat, hands down, jump your feet back to a plank.", "بیٹھیں، ہاتھ زمین پر، پاؤں پیچھے پلانک میں۔"),
            Say("Jump your feet in and explode up with a jump.", "پاؤں آگے لائیں اور چھلانگ لگا کر اوپر آئیں۔"))
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
        COME_CLOSER(Say("Come a little closer.", "تھوڑا قریب آئیں۔")),
        TURN_SIDE(Say("Turn sideways to the camera for this one.", "اس ورزش کے لیے کیمرے کی طرف سائیڈ سے کھڑے ہوں۔")),
        FACE_CAMERA(Say("Face the camera for this one.", "اس ورزش کے لیے کیمرے کی طرف منہ کریں۔")),
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
        ARMS_UP(Say("Arms all the way up.", "بازو پورے اوپر۔")),
        KNEES_UP(Say("Knees up to hip height.", "گھٹنے کولہے کی اونچائی تک۔")),
        HIPS_HIGHER(Say("Squeeze and push your hips higher.", "کولہے دبا کر اور اوپر کریں۔")),
        CURL_HIGHER(Say("Curl up a bit higher.", "تھوڑا اور اوپر آئیں۔")),
        KNEE_90(Say("Slide down to 90 degrees.", "نیچے آئیں، 90 ڈگری تک۔")),
        HEELS_HIGHER(Say("Rise higher on your toes.", "پنجوں پر اور اونچا اٹھیں۔")),
        HEAD_NEUTRAL(Say("Keep your neck long — don't drop your head.", "گردن سیدھی رکھیں، سر نیچے نہ گرائیں۔")),
        LOOK_FORWARD(Say("Eyes forward, head up.", "نظریں سامنے، سر اوپر۔")),
        HEAD_STILL(Say("Keep your head still and facing forward.", "سر سیدھا اور سامنے رکھیں۔")),
    }

    /** Head orientation in degrees from the face model: yaw + = turned to their left, pitch + = looking up, roll + = tilted. */
    data class Head(val yaw: Float, val pitch: Float, val roll: Float) {
        fun describe(): String = when {
            kotlin.math.abs(yaw) > 22f -> if (yaw > 0) "turned left" else "turned right"
            pitch < -20f -> "looking down"
            pitch > 20f -> "looking up"
            kotlin.math.abs(roll) > 15f -> "tilted"
            else -> "level"
        }
    }

    enum class View { SIDE, FRONT, ANY }

    /** 33 landmarks as x,y,likelihood triples in upright image pixels, plus the image size. */
    class Pose(val pts: FloatArray, val w: Int = 0, val h: Int = 0, val head: Head? = null) {
        fun x(i: Int) = pts[i * 3]; fun y(i: Int) = pts[i * 3 + 1]; fun ok(i: Int) = pts[i * 3 + 2] > 0.5f
        fun angle(a: Int, b: Int, c: Int): Float {
            val v1x = x(a) - x(b); val v1y = y(a) - y(b); val v2x = x(c) - x(b); val v2y = y(c) - y(b)
            val d = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
            if (d < 1e-3f) return 180f
            return Math.toDegrees(acos(((v1x * v2x + v1y * v2y) / d).coerceIn(-1f, 1f).toDouble())).toFloat()
        }
        /** Lean of the segment a→b from vertical, degrees. */
        fun lean(a: Int, b: Int) = abs(Math.toDegrees(atan2((x(b) - x(a)).toDouble(), (y(a) - y(b)).toDouble()))).toFloat().let { if (it > 90) 180 - it else it }
        fun dist(a: Int, b: Int) = sqrt((x(a) - x(b)) * (x(a) - x(b)) + (y(a) - y(b)) * (y(a) - y(b)))
        /** 0 = left side, 1 = right side: whichever the camera sees better. */
        fun side(vararg idx: Int): Int {
            val l = idx.sumOf { pts[it * 3 + 2].toDouble() }; val r = idx.sumOf { pts[(it + 1) * 3 + 2].toDouble() }
            return if (r > l) 1 else 0
        }
        /** FRONT when the shoulders look wide compared with the torso, SIDE when they look narrow. */
        fun view(): View? {
            if (!(ok(11) && ok(12) && (ok(23) || ok(24)))) return null
            val sw = dist(11, 12)
            val hy = if (ok(23) && ok(24)) (y(23) + y(24)) / 2 else if (ok(23)) y(23) else y(24)
            val torso = abs(hy - (y(11) + y(12)) / 2).coerceAtLeast(1f)
            val r = sw / torso
            return if (r > 0.5f) View.FRONT else if (r < 0.32f) View.SIDE else View.ANY
        }
    }

    /**
     * How a rep is read from the body. [signal] gives one number per frame (a joint angle or a ratio); a rep starts
     * when it crosses [enter], reaches [target] for a full rep, and ends when it comes back past [start] — the gap
     * between [start] and [enter] is the hysteresis that stops wobble from double-counting.
     */
    class Rule(
        val joints: Set<Int>, val view: View, val startHigh: Boolean,
        val start: Float, val enter: Float, val target: Float, val partial: Float, val partialFix: Fix,
        val signal: (Pose) -> Float?,
    )

    private const val NOSE = 0; private const val SH = 11; private const val EL = 13; private const val WR = 15; private const val HIP = 23
    private const val KN = 25; private const val AN = 27; private const val HEEL = 29; private const val TOE = 31

    /** Angle a-b-c on the side the camera sees best (null when those joints aren't clearly visible). */
    private fun sideAngle(p: Pose, a: Int, b: Int, c: Int): Float? {
        val s = p.side(a, b, c)
        return if (p.ok(a + s) && p.ok(b + s) && p.ok(c + s)) p.angle(a + s, b + s, c + s) else null
    }
    /** The more-bent of the two sides (for alternating moves). */
    private fun minAngle(p: Pose, a: Int, b: Int, c: Int): Float? {
        val l = if (p.ok(a) && p.ok(b) && p.ok(c)) p.angle(a, b, c) else null
        val r = if (p.ok(a + 1) && p.ok(b + 1) && p.ok(c + 1)) p.angle(a + 1, b + 1, c + 1) else null
        return listOfNotNull(l, r).minOrNull()
    }

    fun rule(p: Pattern): Rule? = when (p) {
        Pattern.SQUAT -> Rule(setOf(HIP, KN, AN), View.ANY, true, 158f, 140f, 105f, 128f, Fix.DEEPER) { sideAngle(it, HIP, KN, AN) }
        Pattern.LUNGE -> Rule(setOf(HIP, KN, AN), View.SIDE, true, 158f, 140f, 110f, 132f, Fix.DEEPER) { minAngle(it, HIP, KN, AN) }
        Pattern.HINGE -> Rule(setOf(SH, HIP, KN), View.SIDE, true, 160f, 148f, 125f, 142f, Fix.DEEPER) { sideAngle(it, SH, HIP, KN) }
        Pattern.PUSHUP -> Rule(setOf(SH, EL, WR), View.SIDE, true, 150f, 135f, 100f, 122f, Fix.DEEPER) { sideAngle(it, SH, EL, WR) }
        Pattern.CURL -> Rule(setOf(SH, EL, WR), View.ANY, true, 140f, 120f, 65f, 95f, Fix.FULL) { sideAngle(it, SH, EL, WR) }
        Pattern.OVERHEAD -> Rule(setOf(SH, EL, WR), View.FRONT, false, 105f, 125f, 155f, 138f, Fix.LOCKOUT) { sideAngle(it, SH, EL, WR) }
        Pattern.TRICEP -> Rule(setOf(SH, EL, WR), View.ANY, false, 100f, 118f, 150f, 132f, Fix.LOCKOUT) { sideAngle(it, SH, EL, WR) }
        Pattern.RAISE -> Rule(setOf(HIP, SH, EL), View.FRONT, false, 30f, 45f, 72f, 55f, Fix.FULL) { sideAngle(it, HIP, SH, EL) }
        Pattern.ROW -> Rule(setOf(SH, EL, WR), View.SIDE, true, 145f, 128f, 100f, 118f, Fix.FULL) { sideAngle(it, SH, EL, WR) }
        Pattern.JUMPING_JACK -> Rule(setOf(SH, HIP, WR), View.FRONT, false, 45f, 85f, 140f, 110f, Fix.ARMS_UP) { pp ->
            val l = if (pp.ok(HIP) && pp.ok(SH) && pp.ok(WR)) pp.angle(HIP, SH, WR) else null
            val r = if (pp.ok(HIP + 1) && pp.ok(SH + 1) && pp.ok(WR + 1)) pp.angle(HIP + 1, SH + 1, WR + 1) else null
            listOfNotNull(l, r).takeIf { it.isNotEmpty() }?.average()?.toFloat()
        }
        Pattern.HIGH_KNEES -> Rule(setOf(SH, HIP, KN), View.ANY, true, 150f, 135f, 108f, 122f, Fix.KNEES_UP) { minAngle(it, SH, HIP, KN) }
        Pattern.GLUTE_BRIDGE -> Rule(setOf(SH, HIP, KN), View.SIDE, false, 145f, 155f, 167f, 160f, Fix.HIPS_HIGHER) { sideAngle(it, SH, HIP, KN) }
        Pattern.SITUP, Pattern.CRUNCH -> Rule(setOf(SH, HIP, KN), View.SIDE, true, 120f, 105f, 75f, 92f, Fix.CURL_HIGHER) { sideAngle(it, SH, HIP, KN) }
        Pattern.MOUNTAIN_CLIMBER -> Rule(setOf(SH, HIP, KN), View.SIDE, true, 150f, 128f, 95f, 112f, Fix.KNEES_UP) { minAngle(it, SH, HIP, KN) }
        Pattern.CALF_RAISE -> Rule(setOf(HEEL, TOE, AN), View.SIDE, false, 0.04f, 0.065f, 0.10f, 0.08f, Fix.HEELS_HIGHER) { pp ->
            val s = pp.side(HEEL, TOE, HIP, AN)
            if (!(pp.ok(HEEL + s) && pp.ok(TOE + s) && pp.ok(HIP + s) && pp.ok(AN + s))) null
            else (pp.y(TOE + s) - pp.y(HEEL + s)) / pp.dist(HIP + s, AN + s).coerceAtLeast(1f)
        }
        Pattern.BURPEE -> Rule(setOf(SH, HIP), View.ANY, false, 25f, 45f, 65f, 55f, Fix.FULL) { pp ->
            val s = pp.side(SH, HIP)
            if (pp.ok(SH + s) && pp.ok(HIP + s)) pp.lean(HIP + s, SH + s) else null
        }
        else -> null
    }

    /** Holds the camera can watch (time-based sets). */
    fun holdView(p: Pattern): View? = when (p) { Pattern.PLANK, Pattern.WALL_SIT -> View.SIDE; Pattern.SIDE_PLANK -> View.FRONT; else -> null }
    fun cameraCounts(p: Pattern) = rule(p) != null
    fun cameraHolds(p: Pattern) = holdView(p) != null
    fun requiredView(p: Pattern): View = rule(p)?.view ?: holdView(p) ?: View.ANY
    /** Exercises the camera can follow, for the coach's "what I can watch" list. */
    val cameraList: List<Pair<Pattern, String>> = listOf(
        Pattern.SQUAT to "Squats", Pattern.LUNGE to "Lunges", Pattern.PUSHUP to "Push-ups", Pattern.HINGE to "Deadlifts & RDLs",
        Pattern.CURL to "Curls", Pattern.OVERHEAD to "Shoulder press", Pattern.TRICEP to "Triceps", Pattern.RAISE to "Lateral raises",
        Pattern.ROW to "Rows", Pattern.JUMPING_JACK to "Jumping jacks", Pattern.HIGH_KNEES to "High knees", Pattern.GLUTE_BRIDGE to "Glute bridges",
        Pattern.SITUP to "Sit-ups & crunches", Pattern.MOUNTAIN_CLIMBER to "Mountain climbers", Pattern.CALF_RAISE to "Calf raises",
        Pattern.BURPEE to "Burpees", Pattern.PLANK to "Plank", Pattern.SIDE_PLANK to "Side plank", Pattern.WALL_SIT to "Wall sit",
    )

    /** Before the set: is the whole body in view, at a good distance, from the right angle? */
    data class Setup(val ready: Boolean, val visible: Boolean, val distanceOk: Boolean, val viewOk: Boolean, val fix: Fix?)

    fun setup(pose: Pose, pattern: Pattern): Setup {
        val need = listOf(SH, HIP, KN, AN)
        val vis = need.all { pose.ok(it) || pose.ok(it + 1) } && (pose.ok(NOSE) || pose.ok(SH) || pose.ok(SH + 1))
        if (!vis) return Setup(false, false, false, false, Fix.STEP_BACK)
        val ys = (0 until 33).filter { pose.ok(it) }.map { pose.y(it) }
        val xs = (0 until 33).filter { pose.ok(it) }.map { pose.x(it) }
        val span = if (pose.h > 0) maxOf((ys.max() - ys.min()) / pose.h, (xs.max() - xs.min()) / maxOf(pose.w, 1)) else 0.7f
        val distOk = span in 0.4f..0.97f
        val v = pose.view()
        val want = requiredView(pattern)
        val viewOk = want == View.ANY || v == null || v == View.ANY || v == want
        val fix = when {
            span > 0.97f -> Fix.STEP_BACK
            span < 0.4f -> Fix.COME_CLOSER
            !viewOk -> if (want == View.SIDE) Fix.TURN_SIDE else Fix.FACE_CAMERA
            else -> null
        }
        return Setup(distOk && viewOk, true, distOk, viewOk, fix)
    }

    /**
     * Counts reps from a stream of poses, scores each rep (depth, tempo, alignment) and spots form slips.
     * The signal is smoothed (exponential moving average), gated on landmark confidence, and counted with
     * enter/exit hysteresis and a minimum rep time, so jitter doesn't create phantom reps.
     */
    class Counter(private val pattern: Pattern) {
        data class RepScore(val n: Int, val score: Int, val sec: Float, val depth: Float, val fault: Fix?)
        sealed interface Event {
            data class Rep(val n: Int, val sec: Float, val score: Int, val fault: FormGuide.Fix?) : Event
            data class Cue(val fix: FormGuide.Fix) : Event
            data class Angle(val deg: Float) : Event
        }
        private val r = rule(pattern)
        var reps = 0; private set
        val scores = ArrayList<RepScore>()
        private var moving = false
        private var extreme = 0f
        private var repStart = 0L
        private var lastRepEnd = 0L
        private var ema: Float? = null
        private val times = ArrayList<Float>()
        private var lastFix = 0L
        private var unseen = 0
        private var frames = 0; private var faultFrames = 0
        private val faultCounts = HashMap<Fix, Int>()

        fun feed(p: Pose, now: Long): List<Event> {
            val out = mutableListOf<Event>()
            if (r == null) return hold(p, now)
            val raw = r.signal(p)
            if (raw == null) {
                if (++unseen == 25) fix(out, Fix.STEP_BACK, now, force = true)
                return out
            }
            unseen = 0
            val v = ema?.let { it + 0.45f * (raw - it) } ?: raw
            ema = v
            out += Event.Angle(v)
            val past = { x: Float, t: Float -> if (r.startHigh) x <= t else x >= t }
            val backToStart = if (r.startHigh) v >= r.start else v <= r.start
            if (!moving) {
                if (past(v, r.enter) && now - lastRepEnd > 250) { moving = true; extreme = v; repStart = now; frames = 0; faultFrames = 0; faultCounts.clear() }
            } else {
                extreme = if (r.startHigh) minOf(extreme, v) else maxOf(extreme, v)
                frames++
                formChecks(p, v, now, out)
                if (backToStart) {
                    moving = false
                    lastRepEnd = now
                    val sec = (now - repStart) / 1000f
                    if (sec < 0.35f) return out                       // too fast to be real: jitter
                    if (past(extreme, r.target) || past(extreme, (r.target + r.partial) / 2f)) {
                        reps++
                        times += sec
                        val depth = ((if (r.startHigh) r.start - extreme else extreme - r.start) / abs(r.start - r.target)).coerceIn(0f, 1.2f)
                        val tempo = when { sec < 0.6f -> 0.35f; sec < 1.0f -> 0.75f; sec <= 5f -> 1f; else -> 0.8f }
                        val align = if (frames == 0) 1f else 1f - faultFrames.toFloat() / frames
                        val score = ((depth.coerceAtMost(1f) * 0.5f + tempo * 0.25f + align * 0.25f) * 100).toInt().coerceIn(0, 100)
                        val worst = faultCounts.maxByOrNull { it.value }?.key ?: if (sec < 0.6f) Fix.SLOW else null
                        scores += RepScore(reps, score, sec, depth, worst)
                        out += Event.Rep(reps, sec, score, worst)
                        if (sec < 0.6f) fix(out, Fix.SLOW, now)
                    } else if (past(extreme, r.partial)) fix(out, r.partialFix, now, force = true)
                }
            }
            return out
        }

        /** True when the last rep took much longer than the first ones — time to push. */
        fun slowing(): Boolean = times.size >= 4 && times.last() > 1.6f * times.take(3).average()
        fun average(): Int = if (scores.isEmpty()) 0 else scores.map { it.score }.average().toInt()
        fun topFault(): Fix? = scores.mapNotNull { it.fault }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        private fun formChecks(p: Pose, v: Float, now: Long, out: MutableList<Event>) {
            val s = p.side(SH, HIP, AN)
            val sh = SH + s; val hip = HIP + s; val an = AN + s; val el = EL + s
            val f: Fix? = when (pattern) {
                Pattern.SQUAT -> if (p.ok(sh) && p.ok(hip) && p.lean(hip, sh) > 55f) Fix.CHEST_UP else null
                Pattern.LUNGE -> if (p.ok(sh) && p.ok(hip) && p.lean(hip, sh) > 30f) Fix.STAY_TALL else null
                Pattern.PUSHUP, Pattern.MOUNTAIN_CLIMBER -> if (p.ok(sh) && p.ok(hip) && p.ok(an) && p.angle(sh, hip, an) < 150f) {
                    if (hipAbove(p, sh, hip, an)) Fix.HIPS_DOWN else Fix.HIPS_UP } else null
                Pattern.CURL -> if (p.ok(sh) && p.ok(el) && p.ok(hip) && p.angle(hip, sh, el) > 35f) Fix.ELBOWS else null
                Pattern.RAISE -> if (v > 110f) Fix.TOO_HIGH else null
                else -> null
            } ?: headFault(p, s)
            if (f != null) { faultFrames++; faultCounts[f] = (faultCounts[f] ?: 0) + 1; if (faultCounts[f] == 4) fix(out, f, now) }
        }

        private var holdBadSince = 0L
        private fun hold(p: Pose, now: Long): List<Event> {
            val out = mutableListOf<Event>()
            val s = p.side(SH, HIP, AN); val sh = SH + s; val hip = HIP + s; val an = AN + s; val kn = KN + s
            if (!(p.ok(sh) && p.ok(hip) && p.ok(an))) { if (++unseen == 25) fix(out, Fix.STEP_BACK, now, force = true); return out }
            unseen = 0
            val bad: Fix? = when (pattern) {
                Pattern.WALL_SIT -> if (p.ok(kn)) p.angle(hip, kn, an).also { out += Event.Angle(it) }.let { if (it > 112f) Fix.KNEE_90 else null } else null
                else -> {
                    val line = p.angle(sh, hip, an); out += Event.Angle(line)
                    if (line < 160f) { if (hipAbove(p, sh, hip, an)) Fix.HIPS_DOWN else Fix.HIPS_UP } else null
                }
            } ?: headFault(p, s)
            if (bad != null) {
                if (holdBadSince == 0L) holdBadSince = now
                if (now - holdBadSince > 1200) { fix(out, bad, now); holdBadSince = now }
            } else holdBadSince = 0L
            return out
        }

        /**
         * Head & neck: ear–shoulder–hip angle from the body model catches a dropped head in push-ups and planks; the face
         * model's pitch/yaw catches looking at the floor in standing lifts or turning away mid-set.
         */
        private fun headFault(p: Pose, s: Int): Fix? {
            val ear = 7 + s; val sh = SH + s; val hip = HIP + s
            val prone = pattern in setOf(Pattern.PUSHUP, Pattern.PLANK, Pattern.SIDE_PLANK, Pattern.MOUNTAIN_CLIMBER)
            if (prone && p.ok(ear) && p.ok(sh) && p.ok(hip) && p.angle(ear, sh, hip) < 140f) return Fix.HEAD_NEUTRAL
            val h = p.head ?: return null
            if (!prone && pattern in setOf(Pattern.SQUAT, Pattern.LUNGE, Pattern.HINGE, Pattern.OVERHEAD, Pattern.CURL, Pattern.RAISE) && h.pitch < -32f) return Fix.LOOK_FORWARD
            if (!prone && kotlin.math.abs(h.yaw) > 40f) return Fix.HEAD_STILL
            return null
        }

        /** Image y grows downward: hips "above" the shoulder-ankle line means piked. */
        private fun hipAbove(p: Pose, sh: Int, hip: Int, an: Int): Boolean {
            val t = if (abs(p.x(an) - p.x(sh)) < 1f) 0.5f else (p.x(hip) - p.x(sh)) / (p.x(an) - p.x(sh))
            val lineY = p.y(sh) + t * (p.y(an) - p.y(sh))
            return p.y(hip) < lineY
        }

        private fun fix(out: MutableList<Event>, f: Fix, now: Long, force: Boolean = false) {
            if (!force && now - lastFix < 3500) return
            if (force && now - lastFix < 1500) return
            lastFix = now; out += Event.Cue(f)
        }
    }
}
