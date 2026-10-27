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
        if (AppStore.tutorRemaining() <= 0) { failed = null; return }
        input = ""
        failed = null
        val msg = ChatMessage(newId(), "user", t)
        AppStore.update { it.copy(chat = (it.chat + msg).takeLast(200)) }
        AppStore.consumeTutor()
        AppStore.track("tutor_message", mapOf("mode" to mode))
        thinking = true
        scope.launch {
            runCatching { Ai.tutor(AppStore.state.value.chat, mode, AppStore.learnerContext()) }
                .onSuccess { r ->
                    val reply = ChatMessage(newId(), "assistant", r.text.ifBlank { "Let's try that again. Could you rephrase?" }, r.visual, r.chips)
                    AppStore.update { it.copy(chat = (it.chat + reply).takeLast(200)) }
                    if (AppStore.state.value.tutorVoice) tts?.speak(r.text.replace("**", ""), TextToSpeech.QUEUE_FLUSH, null, reply.id)
                }
                .onFailure { AppStore.logError("tutor", it); failed = t }
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
                Text("Tutor", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                Text("Your AI learning partner", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
            }
            IconButton(onClick = { AppStore.update { it.copy(tutorVoice = !it.tutorVoice) }; if (s.tutorVoice) tts?.stop() }) {
                Icon(if (s.tutorVoice) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff, if (s.tutorVoice) "Turn voice replies off" else "Read replies aloud", tint = if (s.tutorVoice) c.coral else c.inkSoft)
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
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.coralSoft, selectedLabelColor = c.coral, selectedLeadingIconColor = c.coral, containerColor = c.surface),
                )
            }
        }
        HorizontalDivider(color = c.border)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (s.chat.isEmpty()) item { TutorWelcome(s.profile.name) { send(it) } }
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
                    Text("Something didn't load.", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Your message is saved. Check your connection and retry.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Retry", { retry() }, icon = Icons.Rounded.Refresh)
                        TextButton(onClick = { failed = null }) { Text("Dismiss", color = c.inkSoft) }
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

        // Composer
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(28.dp)).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (input.isEmpty()) Text("Ask or say anything", color = c.inkFaint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    input, { input = it }, Modifier.fillMaxWidth().animateContentSize(), maxLines = 5,
                    textStyle = TextStyle(color = c.ink, fontSize = 16.sp), cursorBrush = SolidColor(c.coral),
                    enabled = remaining > 0,
                )
            }
            IconButton(onClick = voice, enabled = remaining > 0) { Icon(Icons.Rounded.Mic, "Speak your question", tint = c.ink) }
            Box(Modifier.height(28.dp).width(1.dp).background(c.border))
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { send(input) }, enabled = input.isNotBlank() && !thinking && remaining > 0,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.coral, contentColor = androidx.compose.ui.graphics.Color.White, disabledContainerColor = c.border),
                modifier = Modifier.size(46.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }
        }
    }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
private fun TutorAvatar() {
    val c = LA.colors
    Box(Modifier.size(44.dp).clip(CircleShape).background(c.cobaltSoft), contentAlignment = Alignment.Center) {
        SparkleMark(Modifier.size(22.dp), c.cobalt)
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
private fun TutorWelcome(name: String, onPick: (String) -> Unit) {
    val c = LA.colors
    val course = AppStore.state.value.courses.firstOrNull { it.id == AppStore.state.value.activeCourseId }
    val ideas = buildList {
        course?.currentLesson?.let { add("Explain ${it.title.lowercase()} simply") }
        add("I'm confused about torque.")
        add("Quiz me on what I learned this week")
        add("Teach me photosynthesis from scratch")
    }
    Column {
        Row {
            TutorAvatar(); Spacer(Modifier.width(14.dp))
            Text(
                "Hi${if (name.isNotBlank()) " $name" else ""}. Ask me anything. I'll explain it, quiz you, or give you just a hint, and I remember what you're learning.",
                style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ideas.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }, shape = CircleShape, colors = AssistChipDefaults.assistChipColors(containerColor = c.cobaltSoft, labelColor = c.cobalt), border = null) }
        }
    }
}
