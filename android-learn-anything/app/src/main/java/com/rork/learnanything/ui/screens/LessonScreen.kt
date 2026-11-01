package com.rork.learnanything.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.LessonContent
import com.rork.learnanything.data.LessonStep
import com.rork.learnanything.data.MatchPair
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LessonScreen(nav: NavController, courseId: String, lessonId: String) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val course = s.courses.firstOrNull { it.id == courseId }
    val lesson = course?.allLessons?.firstOrNull { it.id == lessonId }
    val scope = rememberCoroutineScope()
    var content by remember(lessonId) { mutableStateOf<LessonContent?>(lesson?.content) }
    var loading by remember(lessonId) { mutableStateOf(false) }
    var error by remember(lessonId) { mutableStateOf<String?>(null) }
    var result by remember(lessonId) { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val startedAt = remember(lessonId) { System.currentTimeMillis() }

    fun fallbackContent(): LessonContent {
        val title = lesson?.title ?: "this topic"
        val subject = course?.subject ?: "the subject"
        return LessonContent(
            summary = "A guided starter lesson for $title in $subject.",
            keyPoints = listOf("Understand the core idea first.", "Connect the idea to an example.", "Retrieve the idea from memory before moving on."),
            steps = listOf(
                LessonStep(type = "concept", title = "Start with the big idea", body = "This lesson is about $title. First build a simple mental model of what the idea means and why it matters in $subject.", phase = "learn", concept = title),
                LessonStep(type = "example", title = "Connect it to an example", body = "Think about a real situation where $title could be useful. Connect the new idea to something familiar.", phase = "learn", concept = title),
                LessonStep(type = "concept", title = "Teach it back", body = "Explain $title in your own words as if you were teaching a beginner. If you get stuck, revisit the first step.", phase = "practice", concept = title),
                LessonStep(type = "mcq", title = "Quick check", body = "What should you do first when learning a new concept?", options = listOf("Understand the core idea", "Memorize every detail", "Skip the foundations", "Only take a quiz"), answer = 0, explanation = "A clear mental model makes later details easier to understand and remember.", misconception = "Memorizing details before understanding the idea often creates fragile knowledge.", simpler = "Big picture first, details second.", hint = "Choose the option about understanding before memorizing.", phase = "quiz", concept = title),
                LessonStep(type = "reveal", title = "Self-test", body = "Try to explain the lesson without looking back.", pairs = listOf(MatchPair("Why it matters", "It gives the idea a purpose"), MatchPair("Example", "Makes the idea concrete")), phase = "practice", concept = title),
                LessonStep(type = "concept", title = "Apply it", body = "Identify one real situation where you could use $title. Connecting knowledge to action makes it more useful.", phase = "learn", concept = title)
            )
        )
    }

    fun load() {
        if (course == null || lesson == null) return
        loading = true; error = null
        scope.launch {
            var generated: LessonContent? = null
            var lastFailure: Throwable? = null
            repeat(2) { attempt ->
                runCatching { Ai.generateLesson(course, lesson, AppStore.learnerContext()) }
                    .onSuccess { gen -> if (gen.steps.isNotEmpty()) generated = gen }
                    .onFailure { lastFailure = it }
                if (generated == null && attempt == 0) delay(700)
            }
            val gen = generated ?: fallbackContent()
            content = gen
            AppStore.mapLesson(courseId, lessonId) { it.copy(content = gen) }
            if (generated != null) {
                AppStore.track("lesson_generated", mapOf("lesson" to lesson.title))
            } else {
                AppStore.logError("generateLesson_fallback", lastFailure ?: Exception("AI lesson generation failed"))
                AppStore.track("lesson_fallback", mapOf("lesson" to lesson.title))
            }
            loading = false
        }
    }

    LaunchedEffect(lessonId) { if (content == null) load() }

    DetailScaffold(
        title = lesson?.title ?: "Lesson",
        onBack = { nav.popBackStack() },
        actions = {
            if (lesson != null && result == null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Lesson options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("I already know this") }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) }, onClick = {
                        menu = false
                        AppStore.mapLesson(courseId, lessonId) { it.copy(status = "skipped") }
                        AppStore.update { st -> st.copy(courses = st.courses.map { co -> if (co.id == courseId) co.copy(adaptiveNote = "Adapted for you: you know ${lesson.title.lowercase()}, so we skipped it.") else co }) }
                        AppStore.track("lesson_skipped", mapOf("lesson" to lesson.title))
                        nav.popBackStack()
                    })
                    DropdownMenuItem(text = { Text("Regenerate lesson") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; content = null; load() })
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
            when {
                course == null || lesson == null -> ErrorState(message = "This lesson isn't available anymore.", onBack = { nav.popBackStack() }, onHome = { nav.goHome() })
                result != null -> {
                    val (correct, graded, xp) = result!!
                    val updated = s.courses.firstOrNull { it.id == courseId }
                    val next = updated?.currentLesson
                    CompletionView(title = "Lesson complete", correct = correct, graded = graded, xp = xp + 50, keyPoints = content?.keyPoints.orEmpty(), note = updated?.adaptiveNote, primary = if (next != null) "Next: ${next.title}" else "Back to path", onPrimary = { if (next != null) nav.navigate(Routes.lesson(courseId, next.id)) { popUpTo(Routes.lesson(courseId, lessonId)) { inclusive = true } } else nav.popBackStack() }, secondary = if (next != null) "Back to path" else null, onSecondary = { nav.popBackStack() }, modifier = Modifier.navigationBarsPadding())
                }
                content != null -> {
                    Column(Modifier.fillMaxSize().background(c.canvas)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).background(c.cobaltSoft, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = c.cobalt)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Learn it, then prove it", style = MaterialTheme.typography.labelLarge, color = c.cobalt)
                                Text("Work through each step at your pace. Ask the tutor whenever something is unclear.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        StepPlayer(steps = content!!.steps, subject = course.subject, modifier = Modifier.navigationBarsPadding(), onAskTutor = { step -> AppStore.pendingTutorPrompt.value = "I'm stuck on this from \"${lesson.title}\": ${step.body.ifBlank { step.title }}. Can you explain it another way?"; nav.navigate(Routes.TUTOR) { launchSingleTop = true } }, onFinish = { correct, graded, xp -> val acc = if (graded == 0) 1f else correct.toFloat() / graded; val minutes = ((System.currentTimeMillis() - startedAt) / 60000L).toInt().coerceIn(1, 60); AppStore.completeLesson(courseId, lessonId, acc, minutes); result = Triple(correct, graded, xp) })
                    }
                }
                error != null -> ErrorState(message = error!!, onRetry = { load() }, onBack = { nav.popBackStack() }, onHome = { nav.goHome() })
                else -> GeneratingState(listOf("Designing your lesson…", "Picking the best examples…", "Drawing the visuals…", "Writing practice questions…"))
            }
        }
    }
}
