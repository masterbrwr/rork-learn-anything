package com.rork.learnanything.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.LessonStep
import com.rork.learnanything.data.Narrator
import com.rork.learnanything.data.Segment
import com.rork.learnanything.data.feedbackNarration
import com.rork.learnanything.data.narration
import com.rork.learnanything.data.speakable
import com.rork.learnanything.ui.theme.LA
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val GRADED_TYPES = setOf("mcq", "truefalse", "fill", "match", "order", "estimate", "sort", "tapwords", "engine_label")

fun LessonStep.isGraded() = type in GRADED_TYPES

fun LessonStep.correctAnswerText(): String = when (type) {
    "fill" -> answerText
    "match" -> pairs.joinToString { "${it.left} → ${it.right}" }
    "order" -> items.joinToString(" → ")
    "estimate" -> estimateAnswerText()
    "sort" -> pairs.joinToString { "${it.left} → ${it.right}" }
    "tapwords" -> items.joinToString()
    "engine_label" -> "Spark plug top, valves either side, piston below"
    else -> options.getOrNull(answer) ?: answerText
}

private fun levenshtein(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]; dp[0] = i
        for (j in 1..b.length) {
            val tmp = dp[j]
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return dp[b.length]
}

fun fillMatches(input: String, answer: String): Boolean {
    val norm = { s: String -> s.lowercase().trim().trim('.', '!', '"').replace(Regex("\\s+"), " ").removePrefix("the ").removePrefix("a ") }
    val user = norm(input)
    if (user.isEmpty()) return false
    return answer.split("/", "|", ";").map(norm).filter { it.isNotEmpty() }.any { a ->
        a == user || (a.length >= 5 && levenshtein(a, user) <= 1) || (a.toDoubleOrNull() != null && a.toDoubleOrNull() == user.toDoubleOrNull())
    }
}

/**
 * The unified step player behind lessons, study sessions, quizzes and challenges.
 * Handles Hint / Check / Continue, rich feedback, mistakes, XP, and adaptive follow-up questions.
 */
@Composable
fun StepPlayer(
    steps: List<LessonStep>,
    subject: String,
    modifier: Modifier = Modifier,
    showPhase: Boolean = false,
    adaptive: Boolean = true,
    xpPerCorrect: Int = 10,
    onAnswered: (LessonStep, Boolean) -> Unit = { _, _ -> },
    onAskTutor: ((LessonStep) -> Unit)? = null,
    onFinish: (correct: Int, graded: Int, xp: Int) -> Unit,
) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val list = remember(steps) { steps.toMutableStateList() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var graded by rememberSaveable { mutableIntStateOf(0) }
    var xp by rememberSaveable { mutableIntStateOf(0) }
    var streak by rememberSaveable { mutableIntStateOf(0) }
    var bestStreak by rememberSaveable { mutableIntStateOf(0) }

    var ready by remember(index) { mutableStateOf(false) }
    var pendingCorrect by remember(index) { mutableStateOf(false) }
    var pendingAnswer by remember(index) { mutableStateOf("") }
    var checked by remember(index) { mutableStateOf(false) }
    var showHint by remember(index) { mutableStateOf(false) }
    var loadingSimilar by remember(index) { mutableStateOf(false) }
    var similarError by remember(index) { mutableStateOf(false) }

    if (list.isEmpty()) return
    val step = list[index.coerceIn(0, list.lastIndex)]
    val graded0 = step.isGraded()

    fun advance() {
        if (index >= list.lastIndex) onFinish(correct, graded, xp) else index++
    }

    fun check() {
        checked = true
        graded++
        if (pendingCorrect) {
            correct++; streak++; bestStreak = maxOf(bestStreak, streak)
            xp += xpPerCorrect + if (streak >= 3) 2 else 0 // small streak bonus
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            streak = 0
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            AppStore.addMistake(subject, step, pendingAnswer.ifBlank { "(no answer)" })
        }
        AppStore.recordAnswer(step.concept.ifBlank { subject }, pendingCorrect)
        onAnswered(step, pendingCorrect)
    }

    // ---- Listen instead of read -------------------------------------------------------------
    val app by AppStore.state.collectAsStateWithLifecycle()
    val narr by Narrator.state.collectAsStateWithLifecycle()
    val listenOn = app.listenMode
    val stepKey = "step:$index:${step.hashCode()}"
    val fbKey = "fb:$index:${step.hashCode()}"
    val hintKey = "hint:$index"
    var countdown by remember(index) { mutableStateOf<Int?>(null) }
    val live by rememberUpdatedState(Triple(listenOn, app.listenAutoAdvance, graded0))

    fun narrateCurrent(from: Int = 0) {
        if (checked && graded0) Narrator.play(fbKey, step.feedbackNarration(pendingCorrect, step.correctAnswerText()), from)
        else Narrator.play(stepKey, step.narration(), from)
    }

    LaunchedEffect(index, listenOn, checked) {
        if (!listenOn) { if (Narrator.state.value.key?.let { it.startsWith("step:") || it.startsWith("fb:") || it.startsWith("hint:") } == true) Narrator.stop(); return@LaunchedEffect }
        delay(if (checked) 350 else 450) // let the slide-in settle before speaking
        narrateCurrent()
    }
    LaunchedEffect(showHint) {
        if (showHint && listenOn && step.hint.isNotBlank()) Narrator.play(hintKey, listOf(Segment("Hint. " + speakable(step.hint), "hint")))
    }
    LaunchedEffect(index) {
        Narrator.completed.collect { key ->
            val (on, auto, isGraded) = live
            if (on && auto && ((key == stepKey && !isGraded) || key == fbKey)) countdown = 3
        }
    }
    LaunchedEffect(countdown) {
        val n = countdown ?: return@LaunchedEffect
        if (n <= 0) { countdown = null; advance() } else { delay(1000); if (countdown == n) countdown = n - 1 }
    }
    DisposableEffect(Unit) { onDispose { Narrator.stop() } }
    val focus: Segment? = if (listenOn && (narr.key == stepKey) && narr.status != Narrator.Status.Idle) narr.current else null

    Column(modifier.fillMaxSize()) {
        // Header: step count, XP and segmented progress
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Step ${index + 1} of ${list.size}", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                if (showPhase && step.phase.isNotBlank()) {
                    Pill(step.phase.replaceFirstChar { it.uppercase() }, bg = c.cobaltSoft, fg = c.cobalt)
                    Spacer(Modifier.width(8.dp))
                }
                ListenToggle(listenOn) {
                    val on = !listenOn
                    AppStore.update { it.copy(listenMode = on) }
                    AppStore.track(if (on) "listen_on" else "listen_off")
                }
                Spacer(Modifier.width(8.dp))
                val xpBump = remember { androidx.compose.animation.core.Animatable(1f) }
                LaunchedEffect(xp) { if (xp > 0) { xpBump.snapTo(1.18f); xpBump.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f)) } }
                AnimatedContent(
                    xp, label = "xp",
                    transitionSpec = { (androidx.compose.animation.slideInVertically { it } + fadeIn()) togetherWith (androidx.compose.animation.slideOutVertically { -it } + fadeOut()) },
                    modifier = Modifier.graphicsLayer { scaleX = xpBump.value; scaleY = xpBump.value },
                ) { v ->
                    Pill("+$v XP", icon = Icons.Rounded.WorkspacePremium, bg = c.amberSoft, fg = c.amber)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.indices.forEach { i ->
                    val target = if (i < index || (i == index && checked)) 1f else if (i == index) 0.35f else 0f
                    val f by animateFloatAsState(target, label = "seg")
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(c.border)) {
                        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape).background(c.coral))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = index,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { i ->
            val s = list[i.coerceIn(0, list.lastIndex)]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                StepBody(
                    s, locked = checked && i == index,
                    focus = if (i == index) focus else null,
                    onSentenceTap = if (listenOn && i == index && !checked) ({ sIdx ->
                        val segs = s.narration()
                        val at = segs.indexOfFirst { it.part == "body" && it.index == sIdx }
                        if (at >= 0) { countdown = null; Narrator.play(stepKey, segs, at) }
                    }) else null,
                ) { r, ok, ans ->
                    if (i == index) { ready = r; pendingCorrect = ok; pendingAnswer = ans }
                }
                AnimatedVisibility(showHint && s.hint.isNotBlank() && !checked, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.amberSoft).padding(14.dp),
                    ) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = c.amber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.hint, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                }
                if (checked && i == index && s.isGraded()) {
                    Spacer(Modifier.height(14.dp))
                    FeedbackPanel(
                        step = s, correct = pendingCorrect, streak = streak,
                        loadingSimilar = loadingSimilar, similarError = similarError,
                        onSimilar = if (adaptive && !pendingCorrect) ({
                            loadingSimilar = true; similarError = false
                            scope.launch {
                                runCatching { Ai.similarQuestion(s, easier = true) }
                                    .onSuccess { q ->
                                        list.add(index + 1, q.copy(phase = s.phase, title = "Try a similar one"))
                                        loadingSimilar = false
                                        index++
                                    }
                                    .onFailure { AppStore.logError("similar", it); loadingSimilar = false; similarError = true }
                            }
                        }) else null,
                        onAskTutor = onAskTutor?.let { f -> { f(s) } },
                    )
                }
            }
        }

        // Bottom actions own the bottom edge.
        Surface(color = c.canvas) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                AnimatedVisibility(listenOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    ListenBar(
                        countdown = countdown,
                        onCancelCountdown = { countdown = null },
                        onPlay = {
                            countdown = null
                            val ours = narr.key == stepKey || narr.key == fbKey || narr.key == hintKey
                            when {
                                ours && narr.active -> Narrator.pause()
                                ours && narr.status == Narrator.Status.Paused -> Narrator.resume()
                                else -> narrateCurrent()
                            }
                        },
                        onClose = { countdown = null; AppStore.update { it.copy(listenMode = false) } },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (!graded0 || checked) {
                    PrimaryButton(if (index >= list.lastIndex) "Finish" else "Continue", { advance() }, Modifier.fillMaxWidth(), enabled = !loadingSimilar)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton("Hint", { showHint = true }, Modifier.weight(0.42f), enabled = step.hint.isNotBlank() && !showHint)
                        PrimaryButton("Check", { check() }, Modifier.weight(0.58f), enabled = ready)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackPanel(
    step: LessonStep,
    correct: Boolean,
    streak: Int,
    loadingSimilar: Boolean,
    similarError: Boolean,
    onSimilar: (() -> Unit)?,
    onAskTutor: (() -> Unit)?,
) {
    val c = LA.colors
    val tint = if (correct) c.success else c.coral
    val pop = remember(step) { androidx.compose.animation.core.Animatable(0.4f) }
    LaunchedEffect(step) { pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 500f)) }
    Column(
        Modifier.staggerIn(0, step).shake(if (correct) 0 else 1)
            .fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (correct) c.successSoft else c.coralSoft)
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Burst(correct, Modifier.requiredSize(76.dp), key = step)
                Box(
                    Modifier.size(32.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(tint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (correct) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (correct) (if (streak >= 3) "On a roll." else "Exactly right.") else "Not quite.",
                style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.weight(1f),
            )
            if (correct) ComboBadge(streak)
        }
        if (!correct && step.misconception.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            LabeledLine("What tripped you up", step.misconception)
        }
        if (step.explanation.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            LabeledLine(if (correct) "Why" else "Why it works", step.explanation)
        }
        if (!correct) {
            Spacer(Modifier.height(10.dp))
            LabeledLine("Correct answer", step.correctAnswerText())
            if (step.simpler.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                LabeledLine("A simpler way to see it", step.simpler)
            }
            if (onSimilar != null || onAskTutor != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSimilar != null) AssistChip(
                        onClick = onSimilar, enabled = !loadingSimilar,
                        label = { Text(if (loadingSimilar) "Making one…" else if (similarError) "Retry similar question" else "Try a similar one") },
                        leadingIcon = { if (loadingSimilar) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)) },
                    )
                    if (onAskTutor != null) AssistChip(onClick = onAskTutor, label = { Text("Ask the tutor") })
                }
                if (similarError) Text("Something didn't load. Check your connection.", style = MaterialTheme.typography.bodySmall, color = c.coral)
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, text: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp), color = LA.colors.inkSoft)
    Spacer(Modifier.height(2.dp))
    RichText(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun StepBody(
    step: LessonStep,
    locked: Boolean,
    focus: Segment? = null,
    onSentenceTap: ((Int) -> Unit)? = null,
    onChange: (ready: Boolean, correct: Boolean, answer: String) -> Unit,
) {
    val c = LA.colors
    val bodySentence = if (focus?.part == "body") focus.index else -1
    when (step.type) {
        "concept", "example" -> {
            if (step.type == "example") Pill("Example", bg = c.cobaltSoft, fg = c.cobalt)
            if (step.type == "example") Spacer(Modifier.height(10.dp))
            val titleColor by androidx.compose.animation.animateColorAsState(if (focus?.part == "title") c.cobalt else c.ink, label = "tc")
            Text(step.title, style = MaterialTheme.typography.headlineMedium, color = titleColor)
            Spacer(Modifier.height(12.dp))
            NarratedText(step.body, bodySentence, onSentenceTap = onSentenceTap)
            step.visual?.let { Spacer(Modifier.height(16.dp)); VisualView(it) }
        }
        "engine_sim" -> {
            Text(step.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            if (step.body.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(step.body, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft) }
            Spacer(Modifier.height(12.dp))
            EngineSim()
        }
        "engine_label" -> {
            Text(step.title.ifBlank { "Drag each part to its place" }, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(12.dp))
            EngineLabelGame(locked, onChange)
        }
        "reveal" -> {
            Pill("Flip cards", bg = c.cobaltSoft, fg = c.cobalt)
            Spacer(Modifier.height(10.dp))
            val titleColor by androidx.compose.animation.animateColorAsState(if (focus?.part == "title") c.cobalt else c.ink, label = "tc")
            Text(step.title.ifBlank { "Test yourself" }, style = MaterialTheme.typography.headlineMedium, color = titleColor)
            if (step.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                NarratedText(step.body, bodySentence, onSentenceTap = onSentenceTap)
            }
            Spacer(Modifier.height(16.dp))
            RevealStep(step)
        }
        "tapwords" -> {
            Text("TAP THE WORDS", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = c.coral)
            Spacer(Modifier.height(6.dp))
            val titleColor by androidx.compose.animation.animateColorAsState(if (focus?.part == "title") c.cobalt else c.ink, label = "tc")
            Text(step.title.ifBlank { "Tap the key words" }, style = MaterialTheme.typography.headlineSmall, color = titleColor)
            Spacer(Modifier.height(16.dp))
            TapWordsStep(step, locked, onChange)
        }
        else -> {
            if (step.title.isNotBlank()) {
                Text(step.title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = c.coral)
                Spacer(Modifier.height(6.dp))
            }
            if (step.type != "fill") NarratedText(step.body, bodySentence, style = MaterialTheme.typography.titleLarge)
            step.visual?.let { Spacer(Modifier.height(12.dp)); VisualView(it) }
            Spacer(Modifier.height(16.dp))
            when (step.type) {
                "mcq", "truefalse" -> OptionsStep(step, locked, if (focus?.part == "option") focus.index else -1, onChange)
                "fill" -> FillStep(step, locked, onChange)
                "match" -> MatchStep(step, locked, onChange)
                "order" -> OrderStep(step, locked, onChange)
                "estimate" -> EstimateStep(step, locked, onChange)
                "sort" -> SortStep(step, locked, onChange)
                else -> if (step.options.isNotEmpty()) OptionsStep(step, locked, -1, onChange)
            }
        }
    }
}

@Composable
private fun OptionsStep(step: LessonStep, locked: Boolean, speaking: Int, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    var sel by remember(step) { mutableIntStateOf(-1) }
    val opts = if (step.type == "truefalse" && step.options.size < 2) listOf("True", "False") else step.options
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        opts.forEachIndexed { i, o ->
            val isAns = i == step.answer
            val (bg, border, fg) = when {
                locked && isAns -> Triple(c.successSoft, c.success, c.ink)
                locked && i == sel -> Triple(c.coralSoft, c.coral, c.ink)
                i == sel -> Triple(c.cobaltSoft, c.cobalt, c.ink)
                i == speaking -> Triple(c.surface, c.cobalt.copy(alpha = 0.55f), c.ink)
                else -> Triple(c.surface, c.border, c.ink)
            }
            val bgA by androidx.compose.animation.animateColorAsState(bg, label = "obg")
            val borderA by androidx.compose.animation.animateColorAsState(border, label = "obd")
            Row(
                Modifier.staggerIn(i, step).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgA).border(1.5.dp, borderA, RoundedCornerShape(16.dp))
                    .bouncyClick(enabled = !locked) { sel = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(true, i == step.answer, o) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (i == sel || (locked && isAns)) borderA else c.surfaceAlt),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(('A' + i).toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (i == sel || (locked && isAns)) Color.White else c.inkSoft)
                }
                Spacer(Modifier.width(12.dp))
                Text(o, style = MaterialTheme.typography.bodyLarge, color = fg, modifier = Modifier.weight(1f))
                if (locked && isAns) Icon(Icons.Rounded.Check, "Correct answer", tint = c.success)
            }
        }
    }
}

@Composable
private fun FillStep(step: LessonStep, locked: Boolean, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    var text by remember(step) { mutableStateOf("") }
    val parts = step.body.split("____", "___")
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            parts.forEachIndexed { i, p ->
                append(p)
                if (i < parts.lastIndex) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = c.coral, fontWeight = FontWeight.Bold))
                    append(if (text.isBlank()) " ______ " else " $text ")
                    pop()
                }
            }
        },
        style = MaterialTheme.typography.titleLarge, color = c.ink,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it.isNotBlank(), fillMatches(it, step.answerText), it) },
        enabled = !locked,
        singleLine = true,
        placeholder = { Text("Type your answer") },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MatchStep(step: LessonStep, locked: Boolean, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    val pairColors = listOf(c.cobalt, c.coral, c.success, c.amber, Color(0xFF7E57C2), Color(0xFF00897B))
    val rights = remember(step) { step.pairs.map { it.right }.shuffled() }
    val links = remember(step) { mutableStateMapOf<Int, Int>() } // left -> right index
    var selLeft by remember(step) { mutableIntStateOf(-1) }

    fun report() {
        val ready = links.size == step.pairs.size
        val ok = step.pairs.indices.all { i -> links[i]?.let { rights[it] } == step.pairs[i].right }
        onChange(ready, ok, step.pairs.indices.joinToString { i -> "${step.pairs[i].left} → ${links[i]?.let { rights[it] } ?: "?"}" })
    }
    Text("Tap a term, then its match.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            step.pairs.forEachIndexed { i, p ->
                val linked = links.containsKey(i)
                val col = if (linked) pairColors[i % pairColors.size] else if (selLeft == i) c.cobalt else c.border
                val wrong = locked && links[i]?.let { rights[it] } != p.right
                Box(Modifier.staggerIn(i, step)) { MatchTile(p.left, col, linked || selLeft == i, wrong, locked) { selLeft = i } }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rights.forEachIndexed { j, r ->
                val owner = links.entries.firstOrNull { it.value == j }?.key
                val col = if (owner != null) pairColors[owner % pairColors.size] else c.border
                Box(Modifier.staggerIn(j + 1, step)) { MatchTile(r, col, owner != null, false, locked) {
                    if (selLeft >= 0) {
                        links.entries.firstOrNull { it.value == j }?.let { links.remove(it.key) }
                        links[selLeft] = j
                        selLeft = step.pairs.indices.firstOrNull { !links.containsKey(it) } ?: -1
                        report()
                    } else if (owner != null) { links.remove(owner); report() }
                } }
            }
        }
    }
}

@Composable
private fun MatchTile(text: String, color: Color, active: Boolean, wrong: Boolean, locked: Boolean, onClick: () -> Unit) {
    val c = LA.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.animation.animateColorAsState(if (active) color.copy(alpha = 0.12f) else c.surface, label = "mt").value)
            .border(1.5.dp, if (wrong) c.coral else color, RoundedCornerShape(14.dp))
            .bouncyClick(enabled = !locked, onClick = onClick).padding(10.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderStep(step: LessonStep, locked: Boolean, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    val pool = remember(step) {
        var s = step.items.shuffled()
        var tries = 0
        while (s == step.items && step.items.size > 1 && tries++ < 5) s = step.items.shuffled()
        s
    }
    val seq = remember(step) { mutableStateListOf<String>() }
    fun report() = onChange(seq.size == step.items.size, seq.toList() == step.items, seq.joinToString(" → "))

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceAlt).padding(12.dp).heightIn(min = 60.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (seq.isEmpty()) Text("Tap items below in order", color = c.inkFaint, style = MaterialTheme.typography.bodyMedium)
        seq.forEachIndexed { i, s ->
            val wrong = locked && step.items.getOrNull(i) != s
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)
                    .border(1.5.dp, if (locked) (if (wrong) c.coral else c.success) else c.cobalt, RoundedCornerShape(12.dp))
                    .clickable(enabled = !locked) { seq.removeAt(i); report() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = c.cobalt, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                Text(s, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pool.filter { it !in seq }.forEach { s ->
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .bouncyClick(enabled = !locked) { seq.add(s); report() }.padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(s, style = MaterialTheme.typography.labelLarge, color = c.ink) }
        }
    }
}

/** Shared completion screen for lessons, sessions and quizzes. */
@Composable
fun CompletionView(
    title: String,
    correct: Int,
    graded: Int,
    xp: Int,
    keyPoints: List<String>,
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val c = LA.colors
    val pct = if (graded == 0) 1f else correct.toFloat() / graded
    val haptic = LocalHapticFeedback.current
    val count = remember { androidx.compose.animation.core.Animatable(0f) }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        count.animateTo(1f, androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        if (pct >= 0.6f) { celebrate = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    val shownPct = pct * count.value
    val verdict = when {
        graded == 0 -> "Done"
        pct >= 0.9f -> "Excellent"
        pct >= 0.6f -> "Solid"
        else -> "Keep going"
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                Burst(celebrate, Modifier.fillMaxSize(), rays = 18)
                ProgressRing(shownPct, size = 132.dp, stroke = 10.dp, color = if (pct >= 0.6f) c.success else c.coral, label = "${(shownPct * 100).toInt()}%")
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.staggerIn(0))
            Spacer(Modifier.height(6.dp))
            Text(if (graded > 0) "$correct of $graded correct" else "Completed", color = c.inkSoft, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.staggerIn(1))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().staggerIn(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("+${(xp * count.value).toInt()}", "XP earned", Modifier.weight(1f), accent = c.amber)
                StatTile(if (graded > 0) "$correct/$graded" else "—", "Correct", Modifier.weight(1f))
                StatTile(verdict, "Result", Modifier.weight(1f), accent = if (pct >= 0.6f) c.success else c.coral)
            }
            if (note != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.cobaltSoft).padding(14.dp),
                ) {
                    SparkleMark(Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
            }
            if (keyPoints.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                LACard(Modifier.fillMaxWidth()) {
                    Text("Key takeaways", style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    keyPoints.forEach {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        }
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(primary, onPrimary, Modifier.fillMaxWidth())
            if (secondary != null && onSecondary != null) SecondaryButton(secondary, onSecondary, Modifier.fillMaxWidth(), color = c.inkSoft)
        }
    }
}
