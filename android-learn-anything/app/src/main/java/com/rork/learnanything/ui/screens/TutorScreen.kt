package com.rork.learnanything.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.painterResource
import com.rork.learnanything.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.ChatMessage
import com.rork.learnanything.data.newId
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MODE_ICONS = mapOf(
    "Explain" to Icons.Rounded.ChatBubbleOutline,
    "Practice" to Icons.Rounded.BarChart,
    "Quiz me" to Icons.Rounded.Quiz,
    "Hint" to Icons.Rounded.Lightbulb,
    "Explain differently" to Icons.Rounded.Autorenew,
    "Test me" to Icons.Rounded.Science,
    "Review mistakes" to Icons.Rounded.ErrorOutline,
    "From scratch" to Icons.Rounded.RestartAlt,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutorScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("Explain") }
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var failureReason by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val remaining = AppStore.tutorRemaining(s)
    val pending by AppStore.pendingTutorPrompt.collectAsStateWithLifecycle()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status -> if (status == TextToSpeech.SUCCESS) { engine?.language = Locale.getDefault()
                val st = AppStore.state.value
                engine?.setSpeechRate(st.narratorRate)
                engine?.voices?.firstOrNull { it.name == st.narratorVoice }?.let { v -> runCatching { engine?.voice = v } }
                tts = engine
            } }
        onDispose { engine?.stop(); engine?.shutdown() }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        if (AppStore.tutorRemaining() <= 0) { failed = null; failureReason = null; return }
        input = ""
        failed = null
        failureReason = null
        val msg = ChatMessage(newId(), "user", t)
        AppStore.update { it.copy(chat = (it.chat + msg).takeLast(200)) }
        AppStore.track("tutor_message", mapOf("mode" to mode))
        thinking = true
        scope.launch {
            val tutorState = AppStore.state.value
            val queryTerms = t.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
            val activeCourse = tutorState.courses.firstOrNull { it.id == tutorState.activeCourseId }
            val rankedNotes = tutorState.notes
                .map { note ->
                    val searchable = "${note.title} ${note.subject} ${note.body}".lowercase()
                    val score = queryTerms.count { term -> searchable.contains(term) }
                    note to score
                }
                .sortedWith(compareByDescending<Pair<com.rork.learnanything.data.Note, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            // Prefer notes that match the learner's question. If none match, include
            // recent notes from the active course rather than silently sending no context.
            val matchingNotes = rankedNotes.filter { it.second > 0 }.take(4)
            val fallbackNotes = if (matchingNotes.isEmpty() && activeCourse != null) {
                rankedNotes.filter { it.first.subject.equals(activeCourse.subject, ignoreCase = true) }.take(2)
            } else emptyList()
            val selectedNotes = (matchingNotes + fallbackNotes).distinctBy { it.first.id }
            val courseContext = buildString {
                appendLine("LEARNER'S ACTIVE COURSE (context only; do not pretend it contains facts it does not):")
                activeCourse?.let { course ->
                    appendLine("Course: ${course.title}")
                    appendLine("Subject: ${course.subject}")
                    appendLine("Goal: ${course.goal}")
                    appendLine("Overview: ${course.overview}")
                    if (course.objectives.isNotEmpty()) appendLine("Objectives: ${course.objectives.joinToString("; ")}")
                    course.currentLesson?.let { lesson ->
                        appendLine("Current lesson: ${lesson.title}")
                        lesson.content?.let { content ->
                            appendLine("Lesson summary: ${content.summary}")
                            content.keyPoints.take(8).forEach { appendLine("Key point: $it") }
                            content.steps.take(8).forEach { step ->
                                appendLine("${step.title}: ${step.body}")
                            }
                        }
                    }
                }
                if (selectedNotes.isNotEmpty()) {
                    appendLine("\nSAVED COURSE MATERIALS (use as primary sources when relevant; say when they do not contain the answer):")
                    selectedNotes.forEach { (note, _) ->
                        appendLine("\n[${note.subject} — ${note.title}]")
                        appendLine(note.body.take(2200))
                    }
                }
            }.take(9000).takeIf { activeCourse != null || selectedNotes.isNotEmpty() }.orEmpty()
            runCatching { Ai.tutor(tutorState.chat, mode, AppStore.learnerContext(), courseContext) }
                .onSuccess { r ->
                    AppStore.consumeTutor()
                    val reply = ChatMessage(newId(), "assistant", r.text.ifBlank { "Let's try that again. Could you rephrase?" }, r.visual, r.chips)
                    AppStore.update { it.copy(chat = (it.chat + reply).takeLast(200)) }
                    if (AppStore.state.value.tutorVoice) tts?.speak(r.text.replace("**", ""), TextToSpeech.QUEUE_FLUSH, null, reply.id)
                }
                .onFailure { AppStore.logError("tutor", it); failed = t; failureReason = it.message ?: "Check your connection and try again." }
            thinking = false
        }
    }

    fun retry() {
        val last = failed ?: return
        AppStore.update { st -> st.copy(chat = st.chat.dropLastWhile { it.role == "user" && it.text == last }) }
        failed = null
        send(last)
    }

    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        AppStore.pendingTutorPrompt.value = null
        send(p)
    }
    LaunchedEffect(s.chat.size, thinking, failed) {
        kotlinx.coroutines.delay(50)
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    val voice = rememberVoiceInput { send(it) }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pip", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                Text("Your little garden guide", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
            }
            IconButton(onClick = { AppStore.update { it.copy(tutorVoice = !it.tutorVoice) }; if (s.tutorVoice) tts?.stop() }) {
                Icon(if (s.tutorVoice) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, if (s.tutorVoice) "Turn voice replies off" else "Read replies aloud", tint = if (s.tutorVoice) c.cobalt else c.inkSoft)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Tutor options", tint = c.ink) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Review my mistakes") }, leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null) }, onClick = { menu = false; mode = "Review mistakes"; send("Let's go through my recent mistakes.") })
                    DropdownMenuItem(text = { Text("Teach from scratch") }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) }, onClick = { menu = false; mode = "From scratch" })
                    DropdownMenuItem(text = { Text("Clear conversation") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) }, onClick = { menu = false; failed = null; AppStore.update { it.copy(chat = emptyList()) } })
                }
            }
        }
        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ai.TUTOR_MODES.take(6).forEach { m ->
                FilterChip(
                    selected = mode == m, onClick = { mode = m },
                    label = { Text(m) },
                    leadingIcon = { Icon(MODE_ICONS[m] ?: Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.cobaltSoft, selectedLabelColor = c.cobalt, selectedLeadingIconColor = c.cobalt, containerColor = c.surface),
                )
            }
        }
        HorizontalDivider(color = c.border)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (s.chat.isEmpty()) item { TutorWelcome(s.profile.name, s.courses.firstOrNull { it.id == s.activeCourseId }?.currentLesson?.title) { send(it) } }
            items(s.chat, key = { it.id }) { m ->
                if (m.role == "user") UserBubble(m) else TutorBubble(m, isLast = m.id == s.chat.lastOrNull()?.id && !thinking) { send(it) }
            }
            if (thinking) item("thinking") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TutorAvatar(); Spacer(Modifier.width(14.dp))
                    SparkleMark(Modifier.size(18.dp), c.cobalt, animated = true); Spacer(Modifier.width(8.dp))
                    Text("Thinking…", color = c.inkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (failed != null) item("failed") {
                LACard(Modifier.fillMaxWidth(), color = c.coralSoft, borderColor = c.coral.copy(alpha = 0.25f)) {
                    Text("Your tutor couldn't answer yet", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(failureReason ?: "Check your connection and try again.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                    if (failureReason.orEmpty().contains("configuration", ignoreCase = true) || failureReason.orEmpty().contains("backend", ignoreCase = true) || failureReason.orEmpty().contains("setup", ignoreCase = true)) {
                        Spacer(Modifier.height(4.dp))
                        Text("The AI service still needs to be connected by the app owner. Your message won't count against your daily limit.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Retry", { retry() }, icon = Icons.Rounded.Refresh)
                        TextButton(onClick = { failed = null; failureReason = null }) { Text("Dismiss", color = c.inkSoft) }
                    }
                }
            }
            if (remaining <= 0) item("quota") {
                LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero) {
                    Text("You've used today's free tutor messages", style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Spacer(Modifier.height(4.dp))
                    Text("Free includes ${AppStore.FREE_TUTOR_DAILY} messages a day. They refresh at midnight, or go unlimited with Premium.", color = c.onHero.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("See Premium", { nav.navigate(Routes.PRICING) })
                }
            }
        }

        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(28.dp)).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (input.isEmpty()) Text("Ask or say anything", color = c.inkFaint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    input, { input = it }, Modifier.fillMaxWidth().animateContentSize(), maxLines = 5,
                    textStyle = TextStyle(color = c.ink, fontSize = 16.sp), cursorBrush = SolidColor(c.cobalt),
                    enabled = remaining > 0,
                )
            }
            IconButton(onClick = voice, enabled = remaining > 0) { Icon(Icons.Rounded.Mic, "Speak your question", tint = c.ink) }
            Box(Modifier.height(28.dp).width(1.dp).background(c.border))
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { send(input) }, enabled = input.isNotBlank() && !thinking && remaining > 0,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.cobalt, contentColor = androidx.compose.ui.graphics.Color.White, disabledContainerColor = c.border),
                modifier = Modifier.size(46.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }
        }
    }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
private fun PipMascot(modifier: Modifier = Modifier, compact: Boolean = false) {
    val floatMotion = rememberInfiniteTransition(label = "pip_float")
    val bob by floatMotion.animateFloat(
        initialValue = 0f,
        targetValue = -2.2f,
        animationSpec = infiniteRepeatable(tween(1900), repeatMode = RepeatMode.Reverse),
        label = "pip_bob",
    )
    val green = Color(0xFF4C9B52)
    val lightGreen = Color(0xFF9AD66C)
    val deepGreen = Color(0xFF1D5937)
    val gold = Color(0xFFE8BD59)
    Canvas(modifier.graphicsLayer { translationY = bob * density }) {
        val sx = size.width / 88f
        val sy = size.height / 100f
        val unit = minOf(sx, sy)
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun glossy(x: Float, y: Float, r: Float, color: Color) {
            val center = p(x, y)
            val radius = r * unit
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(red = (color.red * 0.72f + 0.28f).coerceAtMost(1f), green = (color.green * 0.72f + 0.28f).coerceAtMost(1f), blue = (color.blue * 0.72f + 0.28f).coerceAtMost(1f)),
                        color,
                        color.copy(red = color.red * 0.48f, green = color.green * 0.58f, blue = color.blue * 0.52f),
                    ),
                    center = Offset(center.x - radius * 0.28f, center.y - radius * 0.32f),
                    radius = radius * 1.45f,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(Color.White.copy(alpha = 0.22f), radius * 0.12f, Offset(center.x - radius * 0.35f, center.y - radius * 0.42f))
        }
        // Soft contact shadow grounds Pip instead of making him look like a flat sticker.
        drawOval(Color(0xFF183A25).copy(alpha = 0.16f), topLeft = p(5f, 83f), size = Size(75f * sx, 10f * sy))
        if (!compact) {
            // Tiny feet and curved antennae.
            listOf(17f, 34f, 51f, 65f).forEach { x ->
                drawLine(deepGreen, p(x, 76f), p(x - 2f, 88f), 3f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                glossy(x + 1f, 88f, 3.3f, Color(0xFF80553B))
            }
            drawLine(deepGreen, p(64f, 29f), p(59f, 12f), 2.8f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(deepGreen, p(75f, 29f), p(82f, 12f), 2.8f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            glossy(57f, 10f, 4.5f, gold)
            glossy(83f, 10f, 4.5f, lightGreen)
        }
        // Overlapping, shaded body segments with a dark emerald underside.
        glossy(17f, 69f, 14f, deepGreen)
        glossy(17f, 65f, 12.5f, lightGreen)
        glossy(36f, 68f, 15f, deepGreen)
        glossy(36f, 63f, 13.5f, green)
        glossy(54f, 59f, 16f, deepGreen)
        glossy(54f, 54f, 14.5f, lightGreen)
        glossy(68f, 41f, 20f, deepGreen)
        glossy(67f, 37f, 18f, green)
        // Segment seams and tiny highlights add depth.
        listOf(17f to 70f, 36f to 71f, 54f to 62f, 68f to 48f).forEach { (x, y) ->
            drawArc(Color(0xFF16472E).copy(alpha = 0.45f), 15f, 145f, false, p(x - 10f, y - 9f), Size(20f * sx, 16f * sy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.1f * unit))
        }
        // Large friendly eyes with dark irises and glassy catchlights.
        glossy(61f, 34f, 5.6f, Color(0xFFFFFDF4))
        glossy(75f, 34f, 5.6f, Color(0xFFFFFDF4))
        glossy(62f, 35f, 2.8f, Color(0xFF173B2B))
        glossy(76f, 35f, 2.8f, Color(0xFF173B2B))
        drawCircle(Color.White.copy(alpha = 0.92f), 0.9f * unit, p(61f, 33f))
        drawCircle(Color.White.copy(alpha = 0.92f), 0.9f * unit, p(75f, 33f))
        // Smile, warm cheeks, and a small leaf-shaped collar accent.
        drawLine(deepGreen, p(64f, 47f), p(69f, 50f), 1.8f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(deepGreen, p(69f, 50f), p(75f, 47f), 1.8f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        glossy(55f, 43f, 2.8f, Color(0xFFEFA6A0))
        glossy(80f, 43f, 2.8f, Color(0xFFEFA6A0))
        glossy(50f, 76f, 3.8f, gold)
    }
}

@Composable
private fun TutorAvatar() {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        PipMascot(Modifier.fillMaxSize(), compact = true)
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    val c = LA.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)).background(c.surfaceAlt).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(m.text, color = c.ink, style = MaterialTheme.typography.bodyLarge)
        }
        Text(timeFmt.format(Date(m.time)), color = c.inkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
    }
}

@Composable
private fun TutorBubble(m: ChatMessage, isLast: Boolean, onChip: (String) -> Unit) {
    val c = LA.colors
    Column(Modifier.fillMaxWidth()) {
        Row {
            TutorAvatar()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                RichText(m.text)
                Text(timeFmt.format(Date(m.time)), color = c.inkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        m.visual?.let { v ->
            Spacer(Modifier.height(12.dp))
            LACard(Modifier.fillMaxWidth()) { VisualView(v) }
        }
        if (isLast && m.chips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(m.chips) { chip ->
                    AssistChip(
                        onClick = { onChip(chip) }, label = { Text(chip) }, shape = CircleShape,
                        colors = AssistChipDefaults.assistChipColors(containerColor = c.cobaltSoft, labelColor = c.cobalt),
                        border = null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorWelcome(name: String, currentLessonTitle: String?, onPick: (String) -> Unit) {
    val c = LA.colors
    val ideas = buildList {
        currentLessonTitle?.let { add("Explain ${it.lowercase()} simply") }
        add("I'm confused about torque.")
        add("Quiz me on what I learned this week")
        add("Teach me photosynthesis from scratch")
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PipMascot(Modifier.width(92.dp).height(112.dp))
            Surface(
                modifier = Modifier.weight(1f),
                color = c.surface,
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                    Text("Pip", style = MaterialTheme.typography.titleMedium, color = c.cobalt)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hey${if (name.isNotBlank()) " $name" else ""}! I'm Pip. Ask me anything and we'll figure it out together. I can explain, practice, or quiz you!",
                        style = MaterialTheme.typography.bodyMedium, color = c.ink,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Try asking me:", style = MaterialTheme.typography.labelLarge, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ideas.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }, shape = CircleShape, colors = AssistChipDefaults.assistChipColors(containerColor = c.cobaltSoft, labelColor = c.cobalt), border = null) }
        }
    }
}
