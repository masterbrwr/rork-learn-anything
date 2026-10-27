package com.rork.learnanything.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.LessonContent
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun LessonScreen(nav: NavController, courseId: String, lessonId: String) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val course = s.courses.firstOrNull { it.id == courseId }
    val lesson = course?.allLessons?.firstOrNull { it.id == lessonId }
    val scope = rememberCoroutineScope()
    var content by remember(lessonId) { mutableStateOf<LessonContent?>(lesson?.content) }
    var loading by remember(lessonId) { mutableStateOf(false) }
    var error by remember(lessonId) { mutableStateOf<String?>(null) }
    var result by remember(lessonId) { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val startedAt = remember(lessonId) { System.currentTimeMillis() }

    fun load() {
        if (course == null || lesson == null) return
        loading = true; error = null
        scope.launch {
            runCatching { Ai.generateLesson(course, lesson, AppStore.learnerContext()) }
                .onSuccess { gen ->
                    if (gen.steps.isEmpty()) { error = "The lesson came back empty. Try again."; return@onSuccess }
                    content = gen
                    AppStore.mapLesson(courseId, lessonId) { it.copy(content = gen) }
                    AppStore.track("lesson_generated", mapOf("lesson" to lesson.title))
                }
                .onFailure { AppStore.logError("generateLesson", it); error = it.message?.take(160) ?: "Check your connection and try again." }
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
                    CompletionView(
                        title = "Lesson complete",
                        correct = correct, graded = graded, xp = xp + 50,
                        keyPoints = content?.keyPoints.orEmpty(),
                        note = updated?.adaptiveNote,
                        primary = if (next != null) "Next: ${next.title}" else "Back to path",
                        onPrimary = {
                            if (next != null) nav.navigate(Routes.lesson(courseId, next.id)) { popUpTo(Routes.lesson(courseId, lessonId)) { inclusive = true } }
                            else nav.popBackStack()
                        },
                        secondary = if (next != null) "Back to path" else null,
                        onSecondary = { nav.popBackStack() },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
                content != null -> StepPlayer(
                    steps = content!!.steps,
                    subject = course.subject,
                    modifier = Modifier.navigationBarsPadding(),
                    onAskTutor = { step ->
                        AppStore.pendingTutorPrompt.value = "I'm stuck on this from \"${lesson.title}\": ${step.body.ifBlank { step.title }}. Can you explain it another way?"
                        nav.navigate(Routes.TUTOR) { launchSingleTop = true }
                    },
                    onFinish = { correct, graded, xp ->
                        val acc = if (graded == 0) 1f else correct.toFloat() / graded
                        val minutes = ((System.currentTimeMillis() - startedAt) / 60000L).toInt().coerceIn(1, 60)
                        AppStore.completeLesson(courseId, lessonId, acc, minutes)
                        result = Triple(correct, graded, xp)
                    },
                )
                error != null -> ErrorState(message = error!!, onRetry = { load() }, onBack = { nav.popBackStack() }, onHome = { nav.goHome() })
                else -> GeneratingState(listOf("Designing your lesson…", "Picking the best examples…", "Drawing the visuals…", "Writing practice questions…"))
            }
        }
    }
}
