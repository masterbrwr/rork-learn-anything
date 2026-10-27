package com.rork.learnanything.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.LessonStep
import com.rork.learnanything.data.Worlds
import com.rork.learnanything.data.today
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** mode: challenge | quiz | mock | mistakes */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(nav: NavController, mode: String, initialTopic: String, examId: String) {
    val c = LA.colors
    val scope = rememberCoroutineScope()
    val st = AppStore.state.value
    val exam = st.exams.firstOrNull { it.id == examId }
    val autoTopic = when (mode) {
        "challenge" -> st.courses.firstOrNull { it.id == st.activeCourseId }?.let { co -> co.allLessons.filter { it.status == "done" }.lastOrNull()?.title?.let { "${co.title}: $it" } ?: co.title }
            ?: st.profile.interests.firstOrNull() ?: "general knowledge"
        "mock" -> exam?.let { "${it.subject}: ${it.topics}" } ?: initialTopic
        "mistakes" -> initialTopic.ifBlank { st.mistakes.filter { !it.resolved }.takeLast(6).map { it.concept }.distinct().joinToString() }
        else -> initialTopic
    }
    var topic by rememberSaveable { mutableStateOf(autoTopic) }
    var typed by rememberSaveable { mutableStateOf("") }
    var questions by remember { mutableStateOf<List<LessonStep>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    val count = when (mode) { "challenge" -> 3; "mock" -> 10; "mistakes" -> 5; else -> 6 }
    var secondsLeft by remember { mutableIntStateOf(count * 60) }
    var timeUp by remember { mutableStateOf(false) }
    val answers = remember { mutableStateListOf<Boolean>() }
    val title = when (mode) { "challenge" -> "Daily challenge"; "mock" -> "Mock test"; "mistakes" -> "Fix your mistakes"; else -> "Smart quiz" }

    fun load() {
        if (topic.isBlank()) return
        error = null; questions = null
        scope.launch {
            val focus = when (mode) {
                "mistakes" -> "Target these misunderstandings: " + AppStore.state.value.mistakes.filter { !it.resolved }.takeLast(6).joinToString("; ") { "${it.concept} (answered ${it.yourAnswer} instead of ${it.correctAnswer})" }
                "mock" -> "Exam-style, mixed difficulty, cover the breadth of the topics." + (exam?.keyConcepts?.takeIf { it.isNotEmpty() }?.let { " Key concepts: ${it.joinToString()}" } ?: "")
                "challenge" -> "A fun, quick daily challenge. Mix recall and application."
                else -> ""
            }
            val diff = if (mode == "mock") 4 else if (mode == "mistakes") 2 else 3
            runCatching { Ai.generateQuiz(topic, count, diff, AppStore.learnerContext(), focus) }
                .onSuccess { if (it.isEmpty()) error = "No questions came back. Try again." else questions = it.take(count) }
                .onFailure { AppStore.logError("quiz", it); error = it.message?.take(160) ?: "Check your connection." }
        }
    }
    LaunchedEffect(Unit) { if (topic.isNotBlank()) load() }
    LaunchedEffect(questions, result) {
        if (mode != "mock" || questions == null || result != null) return@LaunchedEffect
        while (secondsLeft > 0) { delay(1000); secondsLeft-- }
        timeUp = true
    }

    fun finish(correct: Int, graded: Int, xp: Int) {
        val subject = Worlds.displayName[Worlds.classify(topic)] ?: "General"
        AppStore.finishQuiz(correct, graded, subject, mode)
        var bonus = 0
        when (mode) {
            "challenge" -> {
                bonus = 50
                AppStore.update { it.copy(challengeDay = today(), challengeResults = answers.toList()) }
            }
            "mock" -> if (exam != null) {
                val pct = if (graded == 0) 0 else correct * 100 / graded
                AppStore.update { s -> s.copy(exams = s.exams.map { if (it.id == exam.id) it.copy(mockScores = it.mockScores + pct) else it }) }
            }
            "mistakes" -> if (graded > 0 && correct * 10 >= graded * 8) {
                val concepts = topic.split(",").map { it.trim().lowercase() }.toSet()
                AppStore.update { s -> s.copy(mistakes = s.mistakes.map { if (it.concept.lowercase() in concepts) it.copy(resolved = true) else it }) }
            }
        }
        AppStore.recordActivity(xp + bonus, (count * 1.2f).toInt())
        result = Triple(correct, graded, xp + bonus)
    }
    LaunchedEffect(timeUp) {
        if (timeUp && result == null) { val ok = answers.count { it }; finish(ok, answers.size.coerceAtLeast(count), ok * 10) }
    }

    DetailScaffold(
        title = title, onBack = { nav.popBackStack() },
        actions = {
            if (mode == "mock" && questions != null && result == null) {
                Pill("%d:%02d".format(secondsLeft / 60, secondsLeft % 60), icon = Icons.Rounded.Timer, bg = if (secondsLeft < 60) c.coralSoft else c.surfaceAlt, fg = if (secondsLeft < 60) c.coral else c.ink, modifier = Modifier.padding(end = 12.dp))
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
            when {
                topic.isBlank() -> Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).imePadding()) {
                    Text("What should I quiz you on?", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    Text("Questions adapt to your level and every wrong answer comes with an explanation.", color = c.inkSoft)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Newton's laws") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(12.dp))
                    val ideas = (st.courses.map { it.title } + AppStore.weakTopics(st, 3).map { it.topic } + st.profile.interests).distinct().take(8)
                    if (ideas.isNotEmpty()) {
                        Text("Suggestions", style = MaterialTheme.typography.titleMedium, color = c.inkSoft)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ideas.forEach { i -> AssistChip(onClick = { typed = i }, label = { Text(i) }) } }
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Start quiz", { topic = typed.trim(); load() }, Modifier.fillMaxWidth(), enabled = typed.isNotBlank())
                }
                result != null -> {
                    val (correct, graded, xp) = result!!
                    val pct = if (graded == 0) 0 else correct * 100 / graded
                    CompletionView(
                        title = when { pct >= 90 -> "Excellent"; pct >= 60 -> "Solid work"; else -> "Good practice" },
                        correct = correct, graded = graded, xp = xp, keyPoints = emptyList(),
                        note = when {
                            mode == "challenge" -> "Daily challenge done. +50 XP bonus. Come back tomorrow for a new one."
                            timeUp -> "Time ran out. Unanswered questions count as missed."
                            mode == "mock" && exam != null -> "Score saved to your ${exam.subject} exam plan."
                            pct < 60 -> "Your misses are in the mistake bank. A quick review will lock them in."
                            else -> null
                        },
                        primary = if (pct < 60 && mode != "challenge") "Review my mistakes" else "Done",
                        onPrimary = { if (pct < 60 && mode != "challenge") nav.navigate(Routes.MISTAKES) { popUpTo(Routes.HOME) } else nav.popBackStack() },
                        secondary = if (mode != "challenge") "Another round" else null,
                        onSecondary = { result = null; timeUp = false; answers.clear(); secondsLeft = count * 60; load() },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
                questions != null -> StepPlayer(
                    steps = questions!!, subject = Worlds.displayName[Worlds.classify(topic)] ?: "General",
                    adaptive = mode != "mock",
                    modifier = Modifier.navigationBarsPadding(),
                    onAnswered = { _, ok -> answers += ok },
                    onFinish = { cor, gr, xp -> finish(cor, gr, xp) },
                )
                error != null -> ErrorState(message = error!!, onRetry = { load() }, onBack = { nav.popBackStack() }, onHome = { nav.goHome() })
                else -> GeneratingState(listOf("Writing questions on $topic…", "Calibrating difficulty…", "Preparing feedback…"))
            }
        }
    }
}
