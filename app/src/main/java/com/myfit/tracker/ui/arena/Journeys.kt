package com.myfit.tracker.ui.arena

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick

enum class JourneyTheme(val label: String) { WONDERS("World wonders"), CITIES("City routes"), NATURE("Nature & wildlife"), LEGENDS("Legendary routes") }

/** A stop on a journey: name, distance from the start (km) and one fact you unlock on arrival. */
data class Stop(val name: String, val km: Double, val fact: String)

/**
 * A real-world route walked with your own distance (watch / phone). Distances are approximate route lengths.
 * Art: assets/journey/<id>.webp (FLUX, tools/journeyart).
 */
data class Journey(
    val id: String, val title: String, val place: String, val theme: JourneyTheme, val km: Double,
    val mascot: Mascot, val scene: Scene, val blurb: String, val stops: List<Stop>,
) {
    val stars get() = (stops.size - 2) * 2 + 6
}

val Journeys = listOf(
    // ---- world wonders
    Journey("inca", "Inca Trail to Machu Picchu", "Cusco region, Peru", JourneyTheme.WONDERS, 42.0, Mascot.YAKU, Scene.K2,
        "The classic four-day trail over Andean passes to the lost city of the Incas.", listOf(
            Stop("Km 82 trailhead", 0.0, "The trail starts at Piscacucho, beside the roaring Urubamba River."),
            Stop("Wayllabamba", 12.0, "The last village on the trail — porters stock up here."),
            Stop("Dead Woman's Pass", 19.0, "The highest point of the trail, at 4,215 m."),
            Stop("Wiñay Wayna", 37.0, "Terraced ruins whose name means 'forever young' in Quechua."),
            Stop("Sun Gate", 40.5, "Inti Punku — your first view over Machu Picchu at sunrise."),
            Stop("Machu Picchu", 42.0, "Built around 1450 for the emperor Pachacuti; one of the New Seven Wonders."),
        )),
    Journey("angkor", "Angkor Temples Circuit", "Siem Reap, Cambodia", JourneyTheme.WONDERS, 17.0, Mascot.MOR, Scene.SHALIMAR,
        "The Small Circuit through the jungle temples of the Khmer Empire.", listOf(
            Stop("Angkor Wat", 0.0, "The largest religious monument on Earth, built in the 12th century."),
            Stop("South Gate", 3.0, "A causeway lined with 54 gods on one side and 54 demons on the other."),
            Stop("Bayon", 4.5, "Over 200 serene stone faces gaze out from its towers."),
            Stop("Ta Prohm", 10.0, "Giant tree roots grip the ruins exactly as the jungle left them."),
            Stop("Banteay Kdei", 12.0, "The 'citadel of chambers', facing the royal Srah Srang reservoir."),
            Stop("Back to Angkor Wat", 17.0, "Circuit complete — just in time for sunset."),
        )),
    Journey("greatwall", "Great Wall: Jiankou to Mutianyu", "Beijing, China", JourneyTheme.WONDERS, 10.0, Mascot.MOTU, Scene.MARGALLA,
        "From the wild, crumbling ridges of Jiankou to the restored towers of Mutianyu.", listOf(
            Stop("Jiankou", 0.0, "One of the steepest, wildest stretches of the Ming-dynasty wall."),
            Stop("Zhengbei Tower", 2.5, "A lofty watchtower with views along the ridges in every direction."),
            Stop("Beijing Knot", 4.5, "The point where three branches of the wall meet."),
            Stop("Mutianyu", 10.0, "A restored section with 23 watchtowers — and a toboggan down."),
        )),
    // ---- city routes
    Journey("lahore", "Lahore Heritage Trail", "Lahore, Pakistan", JourneyTheme.CITIES, 5.0, Mascot.PIP, Scene.SHALIMAR,
        "Through the Walled City's bazaars to the Mughal monuments of Lahore.", listOf(
            Stop("Delhi Gate", 0.0, "One of the historic gates of the Walled City of Lahore."),
            Stop("Wazir Khan Mosque", 0.6, "Famed for its 17th-century kashi tile work and frescoes."),
            Stop("Lahore Fort", 2.4, "A UNESCO World Heritage Site, home to the Sheesh Mahal."),
            Stop("Badshahi Mosque", 2.8, "Built by Emperor Aurangzeb in 1673."),
            Stop("Minar-e-Pakistan", 4.0, "Marks where the Lahore Resolution was passed in 1940."),
            Stop("Greater Iqbal Park", 5.0, "One of the largest urban parks in Lahore."),
        )),
    Journey("paris", "Paris Icons", "Paris, France", JourneyTheme.CITIES, 13.0, Mascot.LOMRI, Scene.CITY,
        "From the Eiffel Tower along the Seine to the hilltop of Montmartre.", listOf(
            Stop("Eiffel Tower", 0.0, "Built for the 1889 World's Fair; about 330 m tall today."),
            Stop("Arc de Triomphe", 2.5, "Twelve avenues radiate from it like the points of a star."),
            Stop("Place de la Concorde", 5.0, "Home to a 3,000-year-old Egyptian obelisk."),
            Stop("The Louvre", 6.5, "The world's most-visited art museum."),
            Stop("Notre-Dame", 8.5, "The Gothic cathedral reopened in December 2024 after the 2019 fire."),
            Stop("Sacré-Cœur", 13.0, "The highest point in Paris, on Montmartre hill."),
        )),
    Journey("london", "London Marathon Route", "London, United Kingdom", JourneyTheme.CITIES, 42.2, Mascot.SHAHEEN, Scene.CITY,
        "Run the world's most famous marathon course, from Greenwich to The Mall.", listOf(
            Stop("Greenwich", 0.0, "The race starts on Blackheath, near the Prime Meridian."),
            Stop("Cutty Sark", 10.0, "The 19th-century tea clipper — some of the loudest crowds of the race."),
            Stop("Tower Bridge", 19.5, "Crossing it means you're nearly halfway."),
            Stop("Canary Wharf", 30.0, "The loop through the financial district."),
            Stop("Big Ben", 40.0, "Turn onto Birdcage Walk for the final stretch."),
            Stop("The Mall", 42.2, "Finish in front of Buckingham Palace."),
        )),
    // ---- nature & wildlife
    Journey("canyon", "Grand Canyon Rim to Rim", "Arizona, USA", JourneyTheme.NATURE, 38.0, Mascot.KHARGOSH, Scene.DESERT,
        "Down the North Kaibab Trail, across the Colorado River and up to the South Rim.", listOf(
            Stop("North Rim", 0.0, "The North Kaibab trailhead sits at about 2,500 m."),
            Stop("Supai Tunnel", 3.0, "A short tunnel blasted through the red rock."),
            Stop("Cottonwood Camp", 11.0, "A shady campground beside Bright Angel Creek."),
            Stop("Phantom Ranch", 22.5, "Cross the Colorado River at the bottom of the canyon."),
            Stop("Havasupai Gardens", 30.0, "A green oasis halfway up the Bright Angel Trail."),
            Stop("South Rim", 38.0, "About 1,300 m of climbing from the river — you made it."),
        )),
    Journey("k2", "Baltoro Glacier to K2 Base Camp", "Gilgit-Baltistan, Pakistan", JourneyTheme.NATURE, 90.0, Mascot.ZARA, Scene.K2,
        "One of the world's great treks, up a 60 km glacier into snow-leopard country.", listOf(
            Stop("Askole", 0.0, "The last village before the glacier."),
            Stop("Paiju", 22.0, "Camp with your first view of the Baltoro Glacier."),
            Stop("Urdukas", 45.0, "A grassy ledge above the ice, facing the Trango Towers."),
            Stop("Concordia", 70.0, "The 'throne room of the mountain gods' — four 8,000 m peaks in view."),
            Stop("K2 Base Camp", 90.0, "At the foot of K2 — at 8,611 m, the second-highest mountain on Earth."),
        )),
    Journey("migration", "The Great Migration", "Serengeti to Maasai Mara", JourneyTheme.NATURE, 300.0, Mascot.KALA, Scene.DESERT,
        "Follow the wildebeest herds across Tanzania into Kenya — a season-long journey.", listOf(
            Stop("Ndutu plains", 0.0, "The calving grounds, where thousands of calves are born each day in February."),
            Stop("Seronera", 70.0, "Lion and leopard country in the central Serengeti."),
            Stop("Grumeti River", 140.0, "The first river crossing, home to huge Nile crocodiles."),
            Stop("Lobo", 210.0, "Granite kopjes in the quiet northern Serengeti."),
            Stop("Mara River", 280.0, "The famous crossing, watched from both banks."),
            Stop("Maasai Mara", 300.0, "Well over a million wildebeest arrive by late summer."),
        )),
    // ---- legendary routes
    Journey("camino", "Camino de Santiago", "Sarria to Santiago, Spain", JourneyTheme.LEGENDS, 115.0, Mascot.CHAKOR, Scene.MARGALLA,
        "The final stretch of the thousand-year-old pilgrimage through Galicia.", listOf(
            Stop("Sarria", 0.0, "Walking the last 100 km earns the Compostela certificate."),
            Stop("Portomarín", 22.0, "A village rebuilt stone by stone on the hill above a reservoir."),
            Stop("Palas de Rei", 47.0, "Halfway, through Galicia's oak forests."),
            Stop("Arzúa", 76.0, "Famous for its soft, creamy local cheese."),
            Stop("O Pedrouzo", 96.0, "The last stop before the city."),
            Stop("Santiago Cathedral", 115.0, "Pilgrims have walked here for over a thousand years."),
        )),
    Journey("kkh", "Karakoram Highway", "Hunza to Khunjerab Pass, Pakistan", JourneyTheme.LEGENDS, 160.0, Mascot.SAKEEN, Scene.K2,
        "The old Silk Road route, now one of the highest paved roads on Earth.", listOf(
            Stop("Karimabad", 0.0, "Baltit Fort has watched over the Hunza valley for around 700 years."),
            Stop("Attabad Lake", 20.0, "A turquoise lake formed by a landslide in 2010."),
            Stop("Passu Cones", 50.0, "The jagged 'cathedral' peaks above Passu village."),
            Stop("Sost", 85.0, "The last town before the border."),
            Stop("Khunjerab Pass", 160.0, "At 4,693 m, one of the highest paved border crossings in the world."),
        )),
    Journey("arafat", "The Hajj Journey", "Makkah, Saudi Arabia", JourneyTheme.LEGENDS, 25.0, Mascot.KAMI, Scene.DESERT,
        "Walk the route of the pilgrims between Mina, Arafat and Muzdalifah.", listOf(
            Stop("Mina", 0.0, "The tent city that hosts pilgrims during the days of Hajj."),
            Stop("Arafat", 14.0, "Standing at Arafat on 9 Dhul Hijjah is the heart of Hajj."),
            Stop("Muzdalifah", 21.0, "Pilgrims rest under the open sky and gather pebbles."),
            Stop("Back to Mina", 25.0, "The Jamarat, and the days of Eid al-Adha."),
        )),
)

@Composable
private fun rememberCover(j: Journey): ImageBitmap? {
    val ctx = LocalContext.current
    val a by produceState<ImageBitmap?>(null, j.id) { value = MapArt.get(ctx, "journey/${j.id}.webp") }
    return a
}

private fun km(days: List<Day>, start: java.time.LocalDate) = days.filter { !it.date.isBefore(start) }.sumOf { it.distanceM } / 1000.0

@Composable
fun JourneyHub(container: AppContainer, days: List<Day>, partner: Mascot, ver: Int, changed: () -> Unit, onOpen: (Journey) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var t by remember { mutableIntStateOf(0) }
    val active = remember(t, ver) { ArenaPrefs.journey(ctx) }
    val finished = remember(t, ver) { ArenaPrefs.finished(ctx) }
    var filter by remember { mutableStateOf<JourneyTheme?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val j = active?.let { a -> Journeys.firstOrNull { it.id == a.first } }
        if (active != null && j != null) {
            val done = km(days, active.second)
            LaunchedEffect(done >= j.km) { if (done >= j.km && j.id !in finished) { ArenaPrefs.markFinished(ctx, j.id); toaster.show("Journey complete: ${j.title}"); t++; changed() } }
            val photo = com.myfit.tracker.ui.social.rememberAccountPhoto(container)
            val next = j.stops.firstOrNull { it.km > done }
            RouteMap(rememberCover(j), j.scene.sky + j.scene.hills, (done / j.km).toFloat(),
                j.stops.drop(1).map { s -> RoutePin((s.km / j.km).toFloat(), s.name.take(16), done >= s.km, s == j.stops.last()) },
                if (next == null) "Completed" else "${Fmt.trim(next.km - done, 1)} km to ${next.name}", photo, partner, height = 460.dp,
                header = {
                    Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                        Text(j.theme.label.uppercase(), style = FitType.overline, color = Color.White.copy(alpha = 0.8f))
                        Text(j.title, style = FitType.title, color = Color.White, maxLines = 2)
                        Text("${Fmt.trim(done.coerceAtMost(j.km), 1)} of ${Fmt.trim(j.km, 1)} km", style = FitType.label, color = Color.White.copy(alpha = 0.85f))
                    }
                })
            val last = j.stops.lastOrNull { done >= it.km }
            if (last != null) GlassCard {
                Text(if (next == null) "You made it" else "Unlocked: ${last.name}", style = FitType.section, color = th.text)
                Spacer(Modifier.height(4.dp))
                Text(last.fact, style = FitType.body, color = th.textDim)
            }
            GlassButton("Leave journey", { ArenaPrefs.stopJourney(ctx); t++ }, Modifier.fillMaxWidth(), height = 42.dp)
            Spacer(Modifier.height(4.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GlassChip("All", filter == null, { filter = null }) }
            JourneyTheme.entries.forEach { th0 -> item { GlassChip(th0.label, filter == th0, { filter = th0 }) } }
        }
        Journeys.filter { filter == null || it.theme == filter }.forEach { jj ->
            JourneyCard(jj, jj.id in finished, active?.first == jj.id) { onOpen(jj) }
        }
        Caption("Distance from your watch or phone counts — walking, running and cycling. Route lengths are approximate.", color = th.textFaint)
    }
}

@Composable
private fun JourneyCard(j: Journey, done: Boolean, active: Boolean, onClick: () -> Unit) {
    val tick = rememberTick()
    val cover = rememberCover(j)
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(j.scene.sky + j.scene.hills))
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
        .clickableNoRipple { tick(); onClick() }) {
        cover?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(j.theme.label)
            if (active) Pill("In progress", Color(0xFF4CC38A))
            else if (done) Pill("Completed", Color(0xFFFFC83D))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(j.title, style = FitType.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(j.place, style = FitType.label, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(6.dp))
            Text("${Fmt.trim(j.km, 1)} km  ·  ${j.stops.size - 1} stops  ·  ${j.stars} ★", style = FitType.caption, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun Pill(text: String, color: Color = Color.White) {
    Text(text, style = FitType.caption, color = if (color == Color.White) Color.White else Color(0xFF14161B),
        modifier = Modifier.clip(CircleShape).background(if (color == Color.White) Color.Black.copy(alpha = 0.45f) else color).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** Journey details: cover, story, every stop with its fact, and Start. Rendered at the Arena root. */
@Composable
fun JourneySheet(j: Journey?, days: List<Day>, onDismiss: () -> Unit, onStart: (Journey) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    GlassSheet(visible = j != null, onDismiss = onDismiss) {
        if (j == null) return@GlassSheet
        val active = remember(j) { ArenaPrefs.journey(ctx) }
        val isActive = active?.first == j.id
        val cover = rememberCover(j)
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(j.scene.sky + j.scene.hills))) {
            cover?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(j.title, style = FitType.title, color = Color.White)
                Text(j.place, style = FitType.label, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(j.blurb, style = FitType.body, color = th.text)
        Spacer(Modifier.height(4.dp))
        Caption("${Fmt.trim(j.km, 1)} km · ${j.stops.size - 1} stops · ${j.stars} stars · with ${j.mascot.label.substringBefore(' ')}")
        Spacer(Modifier.height(14.dp))
        val done = if (isActive && active != null) km(days, active.second) else 0.0
        Column(Modifier.animateContentSize()) {
            j.stops.forEachIndexed { i, s ->
                val reached = isActive && done >= s.km
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(if (reached) Color(0xFFFFC83D) else th.text.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            if (i == j.stops.lastIndex) Icon(Duo.Flag, null, tint = if (reached) Color.White else th.textDim, modifier = Modifier.size(12.dp))
                            else Text("${i + 1}", style = FitType.caption, color = if (reached) Color.White else th.textDim)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, style = FitType.label, color = th.text, modifier = Modifier.weight(1f))
                            Text("${Fmt.trim(s.km, 1)} km", style = FitType.caption, color = th.textDim)
                        }
                        Text(s.fact, style = FitType.caption, color = th.textDim)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (isActive) GlassButton("Close", onDismiss, Modifier.fillMaxWidth(), height = 48.dp)
        else AccentButton(if (active != null) "Switch to this journey" else "Start journey", {
            onStart(j); toaster.show("${j.mascot.label.substringBefore(' ')} joins you on ${j.title}")
        }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow)
        Spacer(Modifier.height(8.dp))
    }
}
