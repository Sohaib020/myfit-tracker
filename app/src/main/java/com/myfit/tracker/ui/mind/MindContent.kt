package com.myfit.tracker.ui.mind

/*
 * Static content for the Mind hub: breathing patterns and offline guided-meditation scripts.
 * Scripts carry English, Roman Urdu (shown on screen) and Urdu script (spoken by Pip).
 */

enum class BreathKind { IN, IN2, HOLD, OUT, HOLD_OUT }

/** One phase of a breathing cycle. [voice] is the short word Pip says when the cue is on. */
data class BreathPhase(val kind: BreathKind, val sec: Float) {
    val label: String
        get() = when (kind) {
            BreathKind.IN -> "Breathe in…"
            BreathKind.IN2 -> "Sip in a little more…"
            BreathKind.HOLD, BreathKind.HOLD_OUT -> "Hold…"
            BreathKind.OUT -> "Breathe out…"
        }
    val voice: String
        get() = when (kind) {
            BreathKind.IN -> "In"
            BreathKind.IN2 -> "Again"
            BreathKind.HOLD, BreathKind.HOLD_OUT -> "Hold"
            BreathKind.OUT -> "Out"
        }
}

data class BreathPattern(
    val id: String,
    val name: String,
    val rhythm: String,
    val purpose: String,
    val about: String,
    val phases: List<BreathPhase>,
    /** 0 = accent, 1 = sleep, 2 = water, 3 = success, 4 = warning */
    val hue: Int,
) {
    val cycleSec: Float get() = phases.sumOf { it.sec.toDouble() }.toFloat()
}

val BreathPatterns: List<BreathPattern> = listOf(
    BreathPattern(
        "box", "Box breathing", "4 · 4 · 4 · 4", "Focus",
        "Equal sides: in, hold, out, hold. Used by pilots and athletes to steady attention.",
        listOf(BreathPhase(BreathKind.IN, 4f), BreathPhase(BreathKind.HOLD, 4f), BreathPhase(BreathKind.OUT, 4f), BreathPhase(BreathKind.HOLD_OUT, 4f)),
        2,
    ),
    BreathPattern(
        "478", "4-7-8", "4 · 7 · 8", "Sleep & calm",
        "A long hold and a slow exhale help the body shift toward rest. Great in bed.",
        listOf(BreathPhase(BreathKind.IN, 4f), BreathPhase(BreathKind.HOLD, 7f), BreathPhase(BreathKind.OUT, 8f)),
        1,
    ),
    BreathPattern(
        "coherent", "Coherent", "5.5 · 5.5", "Balance",
        "About five and a half breaths a minute — smooth and even, no holds.",
        listOf(BreathPhase(BreathKind.IN, 5.5f), BreathPhase(BreathKind.OUT, 5.5f)),
        3,
    ),
    BreathPattern(
        "energise", "Energise", "4 · 0 · 2", "Quick lift",
        "Full inhale, brisk exhale. Stop and breathe normally if you feel light-headed.",
        listOf(BreathPhase(BreathKind.IN, 4f), BreathPhase(BreathKind.OUT, 2f)),
        4,
    ),
    BreathPattern(
        "sigh", "Physiological sigh", "2 + 1 · 6", "Fast reset",
        "Two inhales through the nose — a long one, then a short top-up — and a long, slow exhale.",
        listOf(BreathPhase(BreathKind.IN, 2f), BreathPhase(BreathKind.IN2, 1f), BreathPhase(BreathKind.OUT, 6f)),
        0,
    ),
)

/** A guided line. [pause] is the relative weight of the silence that follows it. */
data class MedLine(val en: String, val ro: String, val ur: String, val pause: Float = 1f)

data class MedScript(
    val id: String,
    val title: String,
    val subtitle: String,
    val hue: Int,
    val lines: List<MedLine>,
)

val MedScripts: List<MedScript> = listOf(
    MedScript(
        "calm", "Calm start", "Gentle body scan", 1, listOf(
            MedLine("Welcome. Find a comfortable position, sitting or lying down.", "Khush aamdeed. Aaraam se baith jaayein ya lait jaayein.", "خوش آمدید۔ آرام سے بیٹھ جائیں یا لیٹ جائیں۔", 0.6f),
            MedLine("Let your eyes close, or rest your gaze softly on the floor.", "Aankhein band kar lein, ya nazar narmi se zameen par rakhein.", "آنکھیں بند کر لیں، یا نظر نرمی سے زمین پر رکھیں۔", 0.6f),
            MedLine("Take a slow breath in… and let it go.", "Aahista se saans andar lein… aur chhor dein.", "آہستہ سے سانس اندر لیں… اور چھوڑ دیں۔", 0.8f),
            MedLine("Bring your attention to the top of your head. Notice any feeling there.", "Apni tawajjo sar ke oopar le aayein. Wahan jo mehsoos ho, us par dhyaan dein.", "اپنی توجہ سر کے اوپر لے آئیں۔ وہاں جو محسوس ہو، اس پر دھیان دیں۔"),
            MedLine("Soften your forehead, your eyes, your jaw.", "Maatha, aankhein aur jabra dheela chhor dein.", "ماتھا، آنکھیں اور جبڑا ڈھیلا چھوڑ دیں۔"),
            MedLine("Let your shoulders drop away from your ears.", "Kandhon ko neeche dheela chhor dein.", "کندھوں کو نیچے ڈھیلا چھوڑ دیں۔"),
            MedLine("Feel your arms grow heavy, all the way to your fingertips.", "Baazu bhaari mehsoos karein, ungliyon tak.", "بازو بھاری محسوس کریں، انگلیوں تک۔"),
            MedLine("Notice your chest and belly rising and falling on their own.", "Seene aur pait ko apne aap uthte aur baith te mehsoos karein.", "سینے اور پیٹ کو اپنے آپ اٹھتے اور بیٹھتے محسوس کریں۔", 1.3f),
            MedLine("Let your hips and legs relax into the surface beneath you.", "Kolhon aur taangon ko neeche ki satah par dheela chhor dein.", "کولہوں اور ٹانگوں کو نیچے کی سطح پر ڈھیلا چھوڑ دیں۔"),
            MedLine("Feel your feet. Warm, heavy, resting.", "Apne pairon ko mehsoos karein. Garam, bhaari, pur-sukoon.", "اپنے پیروں کو محسوس کریں۔ گرم، بھاری، پرسکون۔"),
            MedLine("Now sense your whole body at once, breathing gently.", "Ab poore jism ko ek saath mehsoos karein, narmi se saans lete hue.", "اب پورے جسم کو ایک ساتھ محسوس کریں، نرمی سے سانس لیتے ہوئے۔", 1.6f),
            MedLine("When you are ready, wiggle your fingers and open your eyes. Well done.", "Jab tayyar hon, ungliyan hilayein aur aankhein kholein. Bohat khoob.", "جب تیار ہوں، انگلیاں ہلائیں اور آنکھیں کھولیں۔ بہت خوب۔", 0.3f),
        )
    ),
    MedScript(
        "stress", "Stress release", "Let tension melt", 0, listOf(
            MedLine("Let's take a few minutes to release some stress.", "Aaiye kuch minute stress kam karne mein lagaate hain.", "آئیے کچھ منٹ اسٹریس کم کرنے میں لگاتے ہیں۔", 0.5f),
            MedLine("Breathe in through your nose for four… and out through your mouth, slowly.", "Naak se chaar tak saans lein… aur munh se aahista nikaalein.", "ناک سے چار تک سانس لیں… اور منہ سے آہستہ نکالیں۔", 0.8f),
            MedLine("Again. In… and a long breath out, like a sigh.", "Phir se. Andar… aur lambi saans baahar, aah ki tarah.", "پھر سے۔ اندر… اور لمبی سانس باہر، آہ کی طرح۔", 0.8f),
            MedLine("Notice where you hold tension. Maybe your jaw, shoulders, or stomach.", "Dekhein tanaao kahan hai. Shayad jabre, kandhon ya pait mein.", "دیکھیں تناؤ کہاں ہے۔ شاید جبڑے، کندھوں یا پیٹ میں۔"),
            MedLine("Squeeze your fists tight… hold… and let go.", "Mutthiyan zor se band karein… rokein… aur chhor dein.", "مٹھیاں زور سے بند کریں… روکیں… اور چھوڑ دیں۔", 0.7f),
            MedLine("Lift your shoulders up to your ears… hold… and drop them.", "Kandhe kaanon tak uthaayein… rokein… aur gira dein.", "کندھے کانوں تک اٹھائیں… روکیں… اور گرا دیں۔", 0.7f),
            MedLine("Feel the difference between tension and release.", "Tanaao aur sukoon ka farq mehsoos karein.", "تناؤ اور سکون کا فرق محسوس کریں۔"),
            MedLine("Thoughts may come. That's fine. Let them pass like clouds.", "Khayaal aayenge. Koi baat nahi. Unhein baadalon ki tarah guzarne dein.", "خیال آئیں گے۔ کوئی بات نہیں۔ انہیں بادلوں کی طرح گزرنے دیں۔", 1.4f),
            MedLine("With each breath out, let a little more stress leave your body.", "Har saans ke saath thora aur stress jism se nikalne dein.", "ہر سانس کے ساتھ تھوڑا اور اسٹریس جسم سے نکلنے دیں۔", 1.4f),
            MedLine("You don't have to solve everything right now.", "Abhi sab kuch hal karna zaroori nahi.", "ابھی سب کچھ حل کرنا ضروری نہیں۔", 1.2f),
            MedLine("Take one more deep breath, and notice how you feel.", "Ek aur gehri saans lein, aur dekhein ab kaisa mehsoos ho raha hai.", "ایک اور گہری سانس لیں، اور دیکھیں اب کیسا محسوس ہو رہا ہے۔", 0.8f),
            MedLine("Carry this calm with you. Well done.", "Is sukoon ko apne saath rakhein. Shabaash.", "اس سکون کو اپنے ساتھ رکھیں۔ شاباش۔", 0.3f),
        )
    ),
    MedScript(
        "sleep", "Sleep wind-down", "Drift off gently", 1, listOf(
            MedLine("It's time to let the day go. Get comfortable in bed.", "Din ko chhorne ka waqt hai. Bistar par aaraam se lait jaayein.", "دن کو چھوڑنے کا وقت ہے۔ بستر پر آرام سے لیٹ جائیں۔", 0.6f),
            MedLine("Let your breathing slow down, all by itself.", "Saans ko apne aap dheema hone dein.", "سانس کو اپنے آپ دھیما ہونے دیں۔", 0.9f),
            MedLine("Breathe in for four… and out for six… longer out than in.", "Chaar tak andar… aur chhe tak baahar… baahar wali saans lambi.", "چار تک اندر… اور چھ تک باہر… باہر والی سانس لمبی۔", 1.0f),
            MedLine("Feel the weight of your body sinking into the mattress.", "Mehsoos karein jism bistar mein dhans raha hai.", "محسوس کریں جسم بستر میں دھنس رہا ہے۔"),
            MedLine("Your face is soft. Your jaw is loose.", "Chehra narm hai. Jabra dheela hai.", "چہرہ نرم ہے۔ جبڑا ڈھیلا ہے۔"),
            MedLine("Whatever happened today is done. Tomorrow can wait.", "Aaj jo hua, ho gaya. Kal ka intezaar kar sakte hain.", "آج جو ہوا، ہو گیا۔ کل کا انتظار کر سکتے ہیں۔", 1.2f),
            MedLine("Imagine a warm, slow wave moving down from your head to your toes.", "Socheein ek garam, dheemi lehar sar se pairon tak ja rahi hai.", "سوچیں ایک گرم، دھیمی لہر سر سے پیروں تک جا رہی ہے۔", 1.3f),
            MedLine("Each breath out takes you a little deeper into rest.", "Har saans aap ko aur gehre aaraam mein le jaati hai.", "ہر سانس آپ کو اور گہرے آرام میں لے جاتی ہے۔", 1.4f),
            MedLine("There's nothing to do now. Nowhere to be.", "Ab kuch karna nahi. Kahin jaana nahi.", "اب کچھ کرنا نہیں۔ کہیں جانا نہیں۔", 1.4f),
            MedLine("Let your thoughts grow quiet and far away.", "Khayaalon ko khamosh aur door hone dein.", "خیالوں کو خاموش اور دور ہونے دیں۔", 1.6f),
            MedLine("Sleep well.", "Shab bakhair.", "شب بخیر۔", 1.0f),
        )
    ),
    MedScript(
        "focus", "Focus reset", "Clear your head", 2, listOf(
            MedLine("Pause what you're doing. This is a short reset.", "Jo kar rahe hain, roak dein. Yeh ek chhota sa reset hai.", "جو کر رہے ہیں، روک دیں۔ یہ ایک چھوٹا سا ری سیٹ ہے۔", 0.5f),
            MedLine("Sit up tall, feet flat on the floor.", "Seedhe baithein, pair zameen par.", "سیدھے بیٹھیں، پیر زمین پر۔", 0.5f),
            MedLine("Breathe in for four, hold for four, out for four.", "Chaar tak andar, chaar tak rokein, chaar tak baahar.", "چار تک اندر، چار تک روکیں، چار تک باہر۔", 1.0f),
            MedLine("Now rest your attention on the feeling of air at your nose.", "Ab tawajjo naak par hawa ke ehsaas par rakhein.", "اب توجہ ناک پر ہوا کے احساس پر رکھیں۔", 1.3f),
            MedLine("When your mind wanders, gently bring it back. That's the exercise.", "Jab dhyaan bhatke, narmi se waapas laayein. Yahi mashq hai.", "جب دھیان بھٹکے، نرمی سے واپس لائیں۔ یہی مشق ہے۔", 1.4f),
            MedLine("Count your breaths. One… two… up to ten, then start again.", "Saansein ginein. Ek… do… das tak, phir se shuru.", "سانسیں گنیں۔ ایک… دو… دس تک، پھر سے شروع۔", 1.6f),
            MedLine("Notice three sounds around you, without judging them.", "Aas paas ki teen aawaazein suniye, bina raaye ke.", "آس پاس کی تین آوازیں سنیے، بغیر رائے کے۔", 1.0f),
            MedLine("Now choose one thing you'll do next. Just one.", "Ab ek kaam chunein jo aap agla karenge. Sirf ek.", "اب ایک کام چنیں جو آپ اگلا کریں گے۔ صرف ایک۔", 1.0f),
            MedLine("Picture yourself starting it calmly and clearly.", "Tasawwur karein aap use sukoon aur wazahat se shuru kar rahe hain.", "تصور کریں آپ اسے سکون اور وضاحت سے شروع کر رہے ہیں۔", 1.0f),
            MedLine("One last deep breath. You're ready.", "Aakhri gehri saans. Aap tayyar hain.", "آخری گہری سانس۔ آپ تیار ہیں۔", 0.3f),
        )
    ),
    MedScript(
        "gratitude", "Gratitude", "Notice the good", 3, listOf(
            MedLine("Settle in, and take a few easy breaths.", "Aaraam se baith jaayein aur kuch aasaan saansein lein.", "آرام سے بیٹھ جائیں اور کچھ آسان سانسیں لیں۔", 0.7f),
            MedLine("Think of one small thing that went well today.", "Aaj ki koi ek chhoti achhi baat sochein.", "آج کی کوئی ایک چھوٹی اچھی بات سوچیں۔", 1.3f),
            MedLine("Let yourself really feel it, not just think it.", "Use sirf sochein nahi, mehsoos karein.", "اسے صرف سوچیں نہیں، محسوس کریں۔", 1.0f),
            MedLine("Now think of a person you're thankful for.", "Ab kisi aise shakhs ko yaad karein jis ke aap shukarguzaar hain.", "اب کسی ایسے شخص کو یاد کریں جس کے آپ شکرگزار ہیں۔", 1.3f),
            MedLine("Picture their face. Silently say: thank you.", "Un ka chehra tasawwur karein. Dil mein kahein: shukriya.", "ان کا چہرہ تصور کریں۔ دل میں کہیں: شکریہ۔", 1.2f),
            MedLine("Notice something your body did for you today.", "Sochein aaj aap ke jism ne aap ke liye kya kiya.", "سوچیں آج آپ کے جسم نے آپ کے لیے کیا کیا۔", 1.2f),
            MedLine("Your heart beat. Your lungs breathed. Your legs carried you.", "Dil dharka. Saans chali. Taangon ne aap ko uthaaya.", "دل دھڑکا۔ سانس چلی۔ ٹانگوں نے آپ کو اٹھایا۔", 1.0f),
            MedLine("Think of a comfort you often forget — water, a home, a warm meal.", "Koi naimat sochein jo aksar bhool jaate hain — paani, ghar, garam khana.", "کوئی نعمت سوچیں جو اکثر بھول جاتے ہیں — پانی، گھر، گرم کھانا۔", 1.3f),
            MedLine("Let a gentle smile come to your face.", "Chehre par halki si muskurahat aane dein.", "چہرے پر ہلکی سی مسکراہٹ آنے دیں۔", 1.0f),
            MedLine("Carry this feeling into the rest of your day.", "Yeh ehsaas apne din mein saath le jaayein.", "یہ احساس اپنے دن میں ساتھ لے جائیں۔", 0.3f),
        )
    ),
    MedScript(
        "recovery", "Post-workout", "Recover and restore", 4, listOf(
            MedLine("Great work today. Let's help your body recover.", "Aaj bohat achha kaam kiya. Aaiye jism ko recover karne mein madad karein.", "آج بہت اچھا کام کیا۔ آئیے جسم کو ریکور کرنے میں مدد کریں۔", 0.5f),
            MedLine("Lie down or sit comfortably. Let your heart rate settle.", "Lait jaayein ya aaraam se baithein. Dil ki dharkan ko thamne dein.", "لیٹ جائیں یا آرام سے بیٹھیں۔ دل کی دھڑکن کو تھمنے دیں۔", 0.8f),
            MedLine("Breathe in through your nose for four… out slowly for six.", "Naak se chaar tak andar… chhe tak aahista baahar.", "ناک سے چار تک اندر… چھ تک آہستہ باہر۔", 1.0f),
            MedLine("Scan your legs. Notice any warmth or tiredness, without judging it.", "Taangon par dhyaan dein. Garmi ya thakan mehsoos karein, bina raaye ke.", "ٹانگوں پر دھیان دیں۔ گرمی یا تھکن محسوس کریں، بغیر رائے کے۔", 1.0f),
            MedLine("Let your thighs and calves soften with each breath out.", "Har saans ke saath raanon aur pindliyon ko dheela hone dein.", "ہر سانس کے ساتھ رانوں اور پنڈلیوں کو ڈھیلا ہونے دیں۔", 1.0f),
            MedLine("Relax your back, your chest, your arms.", "Kamar, seena aur baazu dheele chhor dein.", "کمر، سینہ اور بازو ڈھیلے چھوڑ دیں۔", 1.0f),
            MedLine("Your muscles grow stronger while you rest. This is part of training.", "Aaraam ke dauraan pathe mazboot hote hain. Yeh bhi training ka hissa hai.", "آرام کے دوران پٹھے مضبوط ہوتے ہیں۔ یہ بھی ٹریننگ کا حصہ ہے۔", 1.3f),
            MedLine("Picture fresh energy flowing into every muscle.", "Tasawwur karein taaza tawanaai har pathe mein ja rahi hai.", "تصور کریں تازہ توانائی ہر پٹھے میں جا رہی ہے۔", 1.3f),
            MedLine("Remember to drink some water and eat well today.", "Aaj paani peena aur achha khana khaana yaad rakhein.", "آج پانی پینا اور اچھا کھانا کھانا یاد رکھیں۔", 0.8f),
            MedLine("Take one more slow breath. Be proud of the work you did.", "Ek aur dheemi saans lein. Apni mehnat par fakhr karein.", "ایک اور دھیمی سانس لیں۔ اپنی محنت پر فخر کریں۔", 0.3f),
        )
    ),
)

val FeelingTags: List<String> = listOf(
    "calm", "happy", "grateful", "motivated", "focused", "excited",
    "tired", "stressed", "anxious", "sad", "angry", "lonely",
)

val MoodWords: List<String> = listOf("Awful", "Bad", "Okay", "Good", "Great")
fun moodWord(m: Int): String = MoodWords.getOrElse(m - 1) { "—" }
