package com.rork.learnanything.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Worlds
import com.rork.learnanything.data.levelFor
import com.rork.learnanything.data.today
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.Serif
import com.rork.learnanything.ui.theme.worldStyle
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

private data class Suggestion(val icon: ImageVector, val label: String, val action: () -> Unit)

private val EXPLORE = listOf(
    "Calculus from scratch", "Python for automation", "Photosynthesis", "How investing works",
    "Conversational Spanish", "Guitar chords", "World War II", "How the brain learns",
)

@Composable
fun HomeScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val voice = rememberVoiceInput { query = it }
    val course = s.courses.firstOrNull { it.id == s.activeCourseId } ?: s.courses.firstOrNull()

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val name = s.profile.name.ifBlank { "there" }

    fun goTutor(prompt: String) {
        AppStore.pendingTutorPrompt.value = prompt
        nav.navigate(Routes.TUTOR) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
    }

    val q = query.trim()
    val suggestions = remember(q, s.courses, s.notes, s.decks) {
        if (q.isEmpty()) emptyList() else buildList {
            val topic = q.removePrefix("teach me ").removePrefix("learn ").removePrefix("how to ").trim().ifBlank { q }
            add(Suggestion(Icons.AutoMirrored.Rounded.MenuBook, "Build a learning path: ${topic.replaceFirstChar { it.uppercase() }}") { nav.navigate(Routes.intake(topic)) })
            add(Suggestion(Icons.Rounded.Schedule, "Start a 15-minute lesson") { nav.navigate(Routes.session(topic, 15)) })
            add(Suggestion(Icons.Rounded.Quiz, "Quiz me on $topic") { nav.navigate(Routes.quiz("quiz", topic)) })
            add(Suggestion(Icons.Rounded.AutoAwesome, "Ask the tutor") { goTutor(q) })
            add(Suggestion(Icons.Rounded.Style, "Make flashcards on $topic") { nav.navigate(Routes.flashcards(topic)) })
            s.courses.filter { it.title.contains(topic, true) || it.subject.contains(topic, true) }.take(2).forEach { co ->
                add(Suggestion(Icons.Rounded.Route, "Open path: ${co.title}") { AppStore.setActiveCourse(co.id); nav.navigate(Routes.PATHS) { launchSingleTop = true } })
            }
            s.notes.filter { it.title.contains(topic, true) || it.body.contains(topic, true) }.take(2).forEach { n ->
                add(Suggestion(Icons.AutoMirrored.Rounded.Notes, "Note: ${n.title.ifBlank { "Untitled" }}") { nav.navigate(Routes.note(n.id)) })
            }
            s.decks.filter { it.name.contains(topic, true) }.take(1).forEach { d ->
                add(Suggestion(Icons.Rounded.Style, "Deck: ${d.name}") { nav.navigate(Routes.deck(d.id)) })
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).statusBarsPadding()) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val dateLine = remember { java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(java.util.Date()) }
                Overline(dateLine)
                Spacer(Modifier.height(4.dp))
                Text("$greeting, $name", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${s.streak} day streak", icon = Icons.Rounded.LocalFireDepartment, bg = c.coralSoft, fg = c.coral)
                    Pill("Level ${levelFor(s.xp)}", icon = Icons.Rounded.WorkspacePremium, bg = c.amberSoft, fg = c.amber)
                }
            }
            Avatar(name, Modifier.pressable({ nav.navigate(Routes.PROFILE) { launchSingleTop = true } }, label = "Open profile"))
        }
        Spacer(Modifier.height(20.dp))

        // Universal ask bar
        LACard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SparkleMark(Modifier.size(22.dp), c.cobalt)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (query.isEmpty()) Text("What do you want to learn?", color = c.inkFaint, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                        cursorBrush = SolidColor(c.cobalt),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (q.isNotEmpty()) { focus.clearFocus(); suggestions.first().action() } }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear", tint = c.inkSoft) }
                IconButton(onClick = voice) { Icon(Icons.Rounded.Mic, "Speak", tint = c.ink) }
            }
            AnimatedVisibility(suggestions.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    suggestions.forEach { sug ->
                        HorizontalDivider(color = c.border, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().pressable({ focus.clearFocus(); sug.action() }).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(sug.icon, null, tint = c.ink, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(sug.label, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Continue hero
        HeroCard(nav)
        Spacer(Modifier.height(16.dp))

        DailyChallengeCard(nav)
        Spacer(Modifier.height(16.dp))

        // Tutor insight
        val weak = AppStore.weakTopics(s, 1).firstOrNull()
        val due = s.cards.count { it.dueDay <= today() }
        val (insight, action) = when {
            weak != null -> "You struggled with ${weak.topic.lowercase()} recently. A 5-minute repair will lock it in." to { nav.navigate(Routes.quiz("mistakes", weak.topic)) }
            due > 0 -> "$due flashcards are due. Reviewing now is when memory sticks best." to { nav.navigate(Routes.review()) }
            course?.currentLesson != null -> "Next up: ${course.currentLesson!!.title}. Ask me anything before you start." to { goTutor("Give me a quick preview of \"${course.currentLesson!!.title}\" before I start the lesson.") }
            else -> "Tell me what you want to learn and I'll build the path." to { goTutor("Help me choose what to learn next.") }
        }
        LACard(Modifier.fillMaxWidth(), onClick = action) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.cobaltSoft), contentAlignment = Alignment.Center) {
                    SparkleMark(Modifier.size(22.dp), c.cobalt)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Overline("Tutor insight", color = c.cobalt)
                    Spacer(Modifier.height(4.dp))
                    Text(insight, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
            }
        }
        Spacer(Modifier.height(16.dp))

        WeeklyChart()
        Spacer(Modifier.height(24.dp))

        SectionHeader("Subject worlds", action = "Study tools", onAction = { nav.navigate(Routes.STUDY) { launchSingleTop = true } })
        Spacer(Modifier.height(8.dp))
    }
    // Worlds row bleeds to the screen edges.
    WorldsRow(nav)
    Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val c = LA.colors
    Box(modifier.size(size).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull()?.uppercase() ?: "L", fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp, color = c.inkSoft)
    }
}

@Composable
private fun HeroCard(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val course = s.courses.firstOrNull { it.id == s.activeCourseId } ?: s.courses.firstOrNull()
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(c.hero).padding(20.dp)) {
        if (course == null || course.isComplete) {
            Column {
                Text(if (course == null) "Start something new" else "You finished ${course.title}", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                Spacer(Modifier.height(6.dp))
                Text("Type any goal and get a personal path in seconds.", color = c.onHero.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                PrimaryButton("Build a path", { nav.navigate(Routes.intake()) }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
            }
        } else {
            val lesson = course.currentLesson!!
            val ws = worldStyle(course.world)
            Row {
                Column(Modifier.weight(1.1f)) {
                    Text("Continue ${course.subject}", style = MaterialTheme.typography.headlineMedium, color = c.onHero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${lesson.title} · ${(course.progress * 100).roundToInt()}%", color = c.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Spacer(Modifier.height(16.dp))
                    ProgressRing(course.progress, size = 72.dp, stroke = 8.dp, track = Color.White.copy(alpha = 0.12f), label = "${(course.progress * 100).roundToInt()}%", labelColor = c.onHero)
                }
                SubjectIllustration(course.world, c.onHero.copy(alpha = 0.9f), ws.accent.copy(alpha = 1f), Modifier.weight(0.9f).height(150.dp))
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "Resume · ${lesson.minutes} min",
                { AppStore.setActiveCourse(course.id); nav.navigate(Routes.lesson(course.id, lesson.id)) },
                Modifier.fillMaxWidth(0.62f),
            )
        }
    }
}

@Composable
private fun DailyChallengeCard(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    var remaining by remember { mutableLongStateOf(msToMidnight()) }
    LaunchedEffect(Unit) { while (true) { remaining = msToMidnight(); delay(30_000) } }
    val doneToday = s.challengeDay == today()
    val results = if (doneToday) s.challengeResults else emptyList()
    LACard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.amberSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = c.amber, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily challenge", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text(if (doneToday) "${results.count { it }} of 3 correct · done" else "3 questions · +50 XP", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { i ->
                        val r = results.getOrNull(i)
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(when (r) { true -> c.coral; false -> c.inkFaint; null -> c.border })
                                .padding(2.dp).clip(CircleShape)
                                .background(if (r == null) c.surface else Color.Transparent)
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val h = remaining / 3_600_000; val m = (remaining / 60_000) % 60
                Pill(if (doneToday) "New in ${h}h ${m}m" else "Ends in ${h}h ${m}m", icon = Icons.Rounded.Schedule, bg = c.coralSoft, fg = c.coral)
                Spacer(Modifier.height(10.dp))
                if (!doneToday) PrimaryButton("Play", { nav.navigate(Routes.quiz("challenge")) }, Modifier.width(120.dp))
                else Pill("Completed", icon = Icons.Rounded.CheckCircle, bg = c.successSoft, fg = c.success)
            }
        }
    }
}

private fun msToMidnight(): Long {
    val now = System.currentTimeMillis()
    val off = TimeZone.getDefault().getOffset(now)
    val dayMs = 86_400_000L
    return dayMs - ((now + off) % dayMs)
}

@Composable
private fun WeeklyChart() {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val d = today()
    val days = (6 downTo 0).map { d - it }
    val mins = days.map { day -> s.activity.firstOrNull { it.day == day }?.minutes ?: 0 }
    val goal = s.profile.dailyGoalMinutes.coerceAtLeast(5)
    val max = (mins.maxOrNull() ?: 0).coerceAtLeast(goal).toFloat()
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    LACard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("This week", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text("${mins.sum()} min learned · goal $goal min/day", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, day ->
                val cal = Calendar.getInstance().apply { timeInMillis = day * 86_400_000L }
                val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val frac = (mins[i] / max).coerceIn(0.04f, 1f)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(frac * 0.8f).clip(RoundedCornerShape(6.dp))
                            .background(if (day == d) c.coral else if (mins[i] >= goal) c.ink else c.border)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(letters[dow], fontSize = 12.sp, color = if (day == d) c.ink else c.inkSoft, fontWeight = if (day == d) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun WorldsRow(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val mine = s.courses.distinctBy { it.world }
    val interestTopics = s.profile.interests.map { "$it basics" }
    val explore = (interestTopics + EXPLORE).filter { t -> mine.none { it.title.contains(t, true) } }.distinct().take(8)
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(mine, key = { "c" + it.id }) { co ->
            val ws = worldStyle(co.world)
            WorldTile(ws.name, co.title, ws.icon, ws.accent, ws.soft, "${(co.progress * 100).roundToInt()}%") {
                AppStore.setActiveCourse(co.id); nav.navigate(Routes.PATHS) { launchSingleTop = true }
            }
        }
        items(explore, key = { "e$it" }) { t ->
            val ws = worldStyle(Worlds.classify(t))
            WorldTile(ws.name, t, ws.icon, ws.accent, ws.soft, "Explore") { nav.navigate(Routes.intake(t)) }
        }
    }
}

@Composable
private fun WorldTile(world: String, title: String, icon: ImageVector, accent: Color, soft: Color, badge: String, onClick: () -> Unit) {
    val c = LA.colors
    LACard(Modifier.width(156.dp).height(150.dp), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(soft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(badge, fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(world.uppercase(), fontSize = 10.sp, letterSpacing = 0.8.sp, color = c.inkSoft, fontWeight = FontWeight.SemiBold)
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
