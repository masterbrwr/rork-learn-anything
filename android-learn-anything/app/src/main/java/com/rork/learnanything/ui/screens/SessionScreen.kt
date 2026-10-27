package com.rork.learnanything.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.LessonContent
import com.rork.learnanything.data.LessonStep
import com.rork.learnanything.data.Worlds
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(nav: NavController, topic: String, minutes: Int) {
    val c = LA.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf<LessonContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var secondsLeft by remember { mutableIntStateOf(minutes * 60) }
    var makingCards by remember { mutableStateOf(false) }
    val missed = remember { mutableStateListOf<LessonStep>() }
    val subject = Worlds.displayName[Worlds.classify(topic)] ?: "General"

    fun load() {
        error = null
        scope.launch {
            runCatching {
                val weak = AppStore.weakTopics().joinToString { it.topic }
                Ai.generateSession(subject, topic + if (weak.isNotBlank()) " (learner is weak on: $weak)" else "", 2, minutes, AppStore.learnerContext())
            }.onSuccess { if (it.steps.isEmpty()) error = "The session came back empty." else { content = it; AppStore.track("session_started", mapOf("topic" to topic)) } }
                .onFailure { AppStore.logError("session", it); error = it.message?.take(160) ?: "Check your connection." }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(content, result) {
        if (content == null || result != null) return@LaunchedEffect
        while (secondsLeft > 0) { delay(1000); secondsLeft-- }
    }

    DetailScaffold(
        title = topic.ifBlank { "Study session" },
        onBack = { nav.popBackStack() },
        actions = {
            if (content != null && result == null) {
                val warn = secondsLeft == 0
                Pill(
                    if (warn) "Time's up" else "%d:%02d".format(secondsLeft / 60, secondsLeft % 60), icon = Icons.Rounded.Timer,
                    bg = if (warn) c.coralSoft else c.surfaceAlt, fg = if (warn) c.coral else c.ink, modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
            when {
                result != null -> {
                    val (correct, graded, xp) = result!!
                    CompletionView(
                        title = "Session complete", correct = correct, graded = graded, xp = xp + 40,
                        keyPoints = content?.keyPoints.orEmpty(),
                        note = if (missed.isNotEmpty()) "Review: ${missed.map { it.concept.ifBlank { it.title } }.distinct().take(3).joinToString()} went to your mistake bank for spaced review." else "Clean run. Nothing to review.",
                        primary = if (makingCards) "Making flashcards…" else "Make flashcards from this",
                        onPrimary = {
                            if (!makingCards) scope.launch {
                                makingCards = true
                                runCatching { Ai.flashcards("Topic: $topic\nKey points:\n" + content?.keyPoints.orEmpty().joinToString("\n") + "\nTricky questions:\n" + missed.joinToString("\n") { it.body }, 8) }
                                    .onSuccess { p ->
                                        val id = AppStore.addDeck(p.deckName.ifBlank { topic }, subject, p.cards.map { it.front to it.back })
                                        nav.navigate(Routes.deck(id)) { popUpTo(Routes.STUDY) }
                                    }
                                    .onFailure { AppStore.logError("sessionCards", it); Toast.makeText(ctx, "Couldn't make cards. Try again.", Toast.LENGTH_SHORT).show() }
                                makingCards = false
                            }
                        },
                        secondary = "Done", onSecondary = { nav.popBackStack() },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
                content != null -> StepPlayer(
                    steps = content!!.steps, subject = subject, showPhase = true,
                    modifier = Modifier.navigationBarsPadding(),
                    onAnswered = { step, ok -> if (!ok) missed += step },
                    onAskTutor = { step ->
                        AppStore.pendingTutorPrompt.value = "Help me with this question about $topic: ${step.body}"
                        nav.navigate(Routes.TUTOR) { launchSingleTop = true }
                    },
                    onFinish = { correct, graded, xp ->
                        val spent = ((minutes * 60 - secondsLeft) / 60).coerceAtLeast(1)
                        AppStore.update { it.copy(sessionsCompleted = it.sessionsCompleted + 1) }
                        AppStore.recordActivity(xp + 40, spent)
                        AppStore.track("session_completed", mapOf("topic" to topic, "score" to "$correct/$graded"))
                        result = Triple(correct, graded, xp)
                    },
                )
                error != null -> ErrorState(message = error!!, onRetry = { load() }, onBack = { nav.popBackStack() }, onHome = { nav.goHome() })
                else -> GeneratingState(listOf("Planning your $minutes minutes…", "Finding your weak spots…", "Building practice…", "Writing the quiz…"))
            }
        }
    }
}
