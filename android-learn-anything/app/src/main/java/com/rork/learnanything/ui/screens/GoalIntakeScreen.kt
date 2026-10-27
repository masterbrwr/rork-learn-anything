package com.rork.learnanything.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.*
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.worldStyle
import kotlinx.coroutines.launch

private val LEVELS = listOf("Complete beginner", "I know the basics", "Intermediate", "Advanced")
private val DEADLINES = listOf("Tomorrow", "This week", "This month", "No rush")
private val TIMES = listOf(10, 20, 30, 60)
private val STYLES = listOf("Visual", "Examples", "Practice", "Theory")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalIntakeScreen(nav: NavController, initialGoal: String) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableStateOf(initialGoal) }
    var level by rememberSaveable { mutableStateOf(LEVELS[0]) }
    var deadline by rememberSaveable { mutableStateOf(DEADLINES[2]) }
    var minutes by rememberSaveable { mutableIntStateOf(s.profile.dailyGoalMinutes.takeIf { it in TIMES } ?: 20) }
    var style by rememberSaveable { mutableStateOf(s.profile.learningStyle.takeIf { it in STYLES } ?: "Examples") }
    var phase by remember { mutableStateOf("form") } // form | loading | error | preview
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf<CourseDraft?>(null) }
    val known = remember { mutableStateListOf<String>() }

    val userPaths = s.courses.count { !it.id.startsWith("seed") }
    val atLimit = !s.isPremium && userPaths >= AppStore.FREE_PATHS

    fun build() {
        if (goal.isBlank()) return
        errorDetail = null
        phase = "loading"
        AppStore.track("path_requested", mapOf("goal" to goal))
        scope.launch {
            runCatching { Ai.generateCourse(goal.trim(), level, deadline, minutes, style, AppStore.learnerContext()) }
                .onSuccess { d ->
                    if (d.modules.isEmpty() || d.modules.all { it.lessons.isEmpty() }) {
                        errorDetail = "The AI service returned an empty course. Please try again."
                        phase = "error"
                    } else {
                        draft = d
                        known.clear()
                        phase = "preview"
                    }
                }
                .onFailure { e ->
                    AppStore.logError("generateCourse", e)
                    errorDetail = when {
                        e.message.orEmpty().contains("setup", true) || e.message.orEmpty().contains("configuration", true) || e.message.orEmpty().contains("backend", true) ->
                            "The AI service is not connected yet. The app's Supabase URL and public key are blank in its configuration, so course generation cannot start until the owner connects the backend."
                        else -> e.message ?: "Check your internet connection and try again."
                    }
                    phase = "error"
                }
        }
    }

    fun start() {
        val d = draft ?: return
        val world = Worlds.classify("$goal ${d.subject} ${d.title}")
        val skipped = d.modules.flatMap { it.lessons }.filter { it.title in known }.map { it.title }
        val note = when {
            skipped.isNotEmpty() -> "Adapted for you: you know ${skipped.take(2).joinToString(" and ") { it.lowercase() }}, so we skipped ${if (skipped.size == 1) "it" else "them"}."
            d.foundationGap.isNotBlank() -> d.foundationGap
            else -> "Built for a ${level.lowercase()} learner with $minutes minutes a day. I'll adjust as you go."
        }
        val course = Course(
            id = newId(), title = d.title.ifBlank { goal }, subject = d.subject.ifBlank { Worlds.displayName[world] ?: "General" },
            world = world, goal = goal, overview = d.overview, objectives = d.objectives, prerequisites = d.prerequisites,
            nextSteps = d.nextSteps, adaptiveNote = note, level = d.level,
            difficulty = when (level) { LEVELS[0] -> 1; LEVELS[1] -> 2; LEVELS[2] -> 3; else -> 4 },
            modules = d.modules.map { m ->
                CourseModule(m.title, m.lessons.map { l ->
                    LessonRef(newId(), l.title, l.minutes.coerceIn(3, 30), status = if (l.title in known) "skipped" else "upcoming")
                })
            },
        )
        AppStore.saveCourse(course)
        AppStore.update { st -> st.copy(profile = st.profile.copy(learningStyle = style, dailyGoalMinutes = minutes)) }
        AppStore.track("path_created", mapOf("title" to course.title, "lessons" to course.total.toString()))
        nav.navigate(Routes.PATHS) { popUpTo(Routes.HOME); launchSingleTop = true }
    }

    DetailScaffold(title = if (phase == "preview") "Your path" else "New learning path", onBack = { if (phase == "preview") phase = "form" else nav.popBackStack() }) { pad ->
        AnimatedContent(phase, Modifier.padding(pad).fillMaxSize(), label = "intake") { p ->
            when (p) {
                "loading" -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    GeneratingState(listOf("Mapping what you need to know…", "Checking prerequisites…", "Sequencing lessons for you…", "Sizing it to your deadline…"))
                }
                "error" -> ErrorState(
                    message = errorDetail ?: "I couldn't build that path just now. Your answers are kept.",
                    onRetry = { build() }, onBack = { phase = "form" }, onHome = { nav.goHome() },
                )
                "preview" -> draft?.let { d -> PathPreview(d, goal, known, onStart = { start() }, onRegenerate = { build() }) }
                else -> Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Text("Your next skill starts here.", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tell us what you want to learn. We'll build a step-by-step path that starts with the basics and grows with you.",
                            style = MaterialTheme.typography.bodyLarge, color = c.inkSoft,
                        )
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(
                            value = goal, onValueChange = { goal = it.take(160) }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. understand car engines, learn algebra") },
                            label = { Text("I want to learn…") },
                            shape = RoundedCornerShape(18.dp), minLines = 2, maxLines = 3,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "We'll teach the fundamentals first, show worked examples, and add memory tricks when they help.",
                            style = MaterialTheme.typography.bodyMedium, color = c.inkSoft,
                        )
                        if (atLimit) {
                            Spacer(Modifier.height(14.dp))
                            LACard(Modifier.fillMaxWidth(), color = c.amberSoft, borderColor = c.amber.copy(alpha = 0.3f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Lock, null, tint = c.amber)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Free includes ${AppStore.FREE_PATHS} AI paths. Delete one or go Premium for unlimited.", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                                }
                                TextButton(onClick = { nav.navigate(Routes.PRICING) }) { Text("See Premium", color = c.coral) }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        LACard(Modifier.fillMaxWidth(), onClick = { showPreferences = !showPreferences }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(c.cobaltSoft),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Tune, null, tint = c.cobalt) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Personalize your path", style = MaterialTheme.typography.titleMedium, color = c.ink)
                                    Text(
                                        if (showPreferences) "Adjust your level, pace and learning style" else "Optional · choose your pace and starting level",
                                        style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
                                    )
                                }
                                Icon(
                                    if (showPreferences) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (showPreferences) "Hide preferences" else "Show preferences",
                                    tint = c.inkSoft,
                                )
                            }
                        }
                        AnimatedVisibility(showPreferences, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column {
                                ChipQuestion("Your starting level", LEVELS, level) { level = it }
                                ChipQuestion("Your timeline", DEADLINES, deadline) { deadline = it }
                                ChipQuestion("Daily learning time", TIMES.map { "$it min" }, "$minutes min") { minutes = it.removeSuffix(" min").toInt() }
                                ChipQuestion("How you prefer to learn", STYLES, style) { style = it }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Box(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        PrimaryButton("Build my path", { build() }, Modifier.fillMaxWidth(), enabled = goal.isNotBlank() && !atLimit, icon = Icons.Rounded.AutoAwesome)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipQuestion(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, color = LA.colors.ink)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) },
                leadingIcon = if (o == selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun PathPreview(d: CourseDraft, goal: String, known: MutableList<String>, onStart: () -> Unit, onRegenerate: () -> Unit) {
    val c = LA.colors
    val ws = worldStyle(Worlds.classify("$goal ${d.subject} ${d.title}"))
    val total = d.modules.sumOf { m -> m.lessons.filter { it.title !in known }.sumOf { it.minutes } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(ws.name, icon = ws.icon, bg = ws.soft, fg = ws.accent)
                Spacer(Modifier.width(8.dp))
                if (d.level.isNotBlank()) Pill(d.level)
                Spacer(Modifier.width(8.dp))
                Pill("About ${minutesLabel(total)}")
            }
            Spacer(Modifier.height(12.dp))
            Text(d.title, style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(d.overview, style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
            if (d.foundationGap.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.amberSoft).padding(14.dp)) {
                    Icon(Icons.Rounded.Warning, null, tint = c.amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Foundation check", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(d.foundationGap, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            LACard(Modifier.fillMaxWidth()) {
                Text("You'll be able to", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(6.dp))
                d.objectives.forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                }
                if (d.prerequisites.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Builds on: ${d.prerequisites.joinToString()}", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Already know something? Tick it and I'll skip it.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            d.modules.forEachIndexed { mi, m ->
                Spacer(Modifier.height(16.dp))
                Text("MODULE ${mi + 1}", style = MaterialTheme.typography.labelSmall, color = c.coral, fontWeight = FontWeight.Bold)
                Text(m.title, style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(8.dp))
                m.lessons.forEach { l ->
                    val isKnown = l.title in known
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                            .pressable({ if (isKnown) known.remove(l.title) else known.add(l.title) })
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.bodyLarge, color = if (isKnown) c.inkFaint else c.ink)
                            Text(if (isKnown) "skipping" else "${l.minutes} min", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        }
                        Text("I know this", style = MaterialTheme.typography.labelMedium, color = c.inkSoft)
                        Checkbox(checked = isKnown, onCheckedChange = { if (it) known.add(l.title) else known.remove(l.title) })
                    }
                }
            }
            if (d.nextSteps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("After this: ${d.nextSteps.joinToString()}", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Rebuild", onRegenerate, Modifier.weight(0.4f), color = c.inkSoft)
            PrimaryButton("Start learning", onStart, Modifier.weight(0.6f))
        }
    }
}
