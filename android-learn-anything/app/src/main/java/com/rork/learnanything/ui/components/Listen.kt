package com.rork.learnanything.ui.components

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Narrator
import com.rork.learnanything.data.splitSentences
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.OverlineStyle
import com.rork.learnanything.ui.theme.Tabular

fun speedLabel(r: Float): String = (if (r % 1f == 0f) "%.0f" else if ((r * 10) % 1f == 0f) "%.1f" else "%.2f").format(r) + "×"

/** Header pill that turns "Listen instead of read" on and off. */
@Composable
fun ListenToggle(on: Boolean, onClick: () -> Unit) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (on) c.hero else c.surface, label = "lbg")
    val fg by animateColorAsState(if (on) c.onHero else c.ink, label = "lfg")
    Row(
        Modifier.clip(CircleShape).background(bg).border(1.dp, if (on) bg else c.border, CircleShape)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .semantics { contentDescription = if (on) "Stop listening" else "Listen instead of read" }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Headphones, null, tint = if (on) c.coral else fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (on) "Listening" else "Listen", color = fg, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
    }
}

/**
 * Body text that highlights the sentence being narrated, Apple Books style.
 * When [onSentenceTap] is set, tapping any sentence starts reading from there.
 */
@Composable
fun NarratedText(
    raw: String,
    activeSentence: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    onSentenceTap: ((Int) -> Unit)? = null,
) {
    val c = LA.colors
    val parts = remember(raw) { splitSentences(raw) }
    val hl = c.cobaltSoft
    val starts = remember(raw) { IntArray(parts.size) }
    val text: AnnotatedString = remember(raw, activeSentence, c) {
        buildAnnotatedString {
            parts.forEachIndexed { i, (sentence, sep) ->
                starts[i] = length
                val piece = richText(sentence, c.ink, c.amberSoft)
                if (i == activeSentence) withStyle(SpanStyle(background = hl)) { append(piece) } else append(piece)
                append(sep)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = style, color = c.ink, onTextLayout = { layout = it },
        modifier = modifier.then(
            if (onSentenceTap == null) Modifier else Modifier.pointerInput(raw) {
                detectTapGestures { pos ->
                    val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                    val idx = starts.indexOfLast { it <= off }.coerceAtLeast(0)
                    onSentenceTap(idx)
                }
            }
        ),
    )
}

/** Live voice bars; settle to a flat line when not speaking. */
@Composable
private fun VoiceBars(active: Boolean, color: Color) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val amp by animateFloatAsState(if (active) 1f else 0f, tween(300, easing = FastOutSlowInEasing), label = "amp")
    Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0f, 0.35f, 0.7f, 0.15f, 0.5f).forEach { off ->
            val v = kotlin.math.abs(kotlin.math.sin((phase + off) * Math.PI * 2)).toFloat()
            val h = 4f + 18f * v * amp
            Box(Modifier.width(3.dp).height(h.dp).clip(CircleShape).background(color))
        }
    }
}

/**
 * Navy mini player that sits above the lesson's bottom actions while listening.
 * Play / pause, sentence skip, speed, live captions, and an auto-advance countdown.
 */
@Composable
fun ListenBar(
    countdown: Int?,
    onCancelCountdown: () -> Unit,
    onPlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LA.colors
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val st by Narrator.state.collectAsStateWithLifecycle()
    val app by AppStore.state.collectAsStateWithLifecycle()
    val playing = st.active
    val progress by animateFloatAsState(st.progress, tween(450), label = "prog")
    val dim = c.onHero.copy(alpha = 0.62f)

    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.hero)) {
        if (st.unavailable) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = c.coral)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Voice isn't set up on this phone", style = MaterialTheme.typography.titleSmall, color = c.onHero)
                    Text("Install or enable Speech Services by Google, then try again.", style = MaterialTheme.typography.bodySmall, color = dim)
                }
                TextButton(onClick = {
                    runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .recoverCatching { ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Set up", color = c.coral) }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Stop listening", tint = dim) }
            }
            return@Column
        }

        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "ps")
            Box(
                Modifier.size(48.dp).scale(scale).clip(CircleShape).background(c.coral)
                    .clickable(interactionSource = press, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlay()
                    }
                    .semantics { contentDescription = if (playing) "Pause" else "Play" },
                contentAlignment = Alignment.Center,
            ) {
                if (st.status == Narrator.Status.Preparing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            countdown != null -> "UP NEXT"
                            playing -> "NOW READING"
                            st.status == Narrator.Status.Paused -> "PAUSED"
                            else -> "LISTEN MODE"
                        },
                        style = OverlineStyle, color = c.coral,
                    )
                    if (st.segments.size > 1 && countdown == null) {
                        Text("  ${st.index + 1}/${st.segments.size}", style = OverlineStyle.merge(Tabular), color = dim)
                    }
                }
                Spacer(Modifier.height(3.dp))
                AnimatedContent(
                    targetState = if (countdown != null) "Next step in ${countdown}s" else st.current?.text ?: "Tap play to hear this step.",
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(160)) + slideOutVertically { -it / 3 }) },
                    label = "caption",
                ) { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = c.onHero, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            VoiceBars(playing, c.onHero.copy(alpha = 0.8f))
            Spacer(Modifier.width(4.dp))
        }

        Row(Modifier.padding(horizontal = 6.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (countdown != null) {
                TextButton(onClick = onCancelCountdown) { Text("Stay on this step", color = c.onHero) }
                Spacer(Modifier.weight(1f))
            } else {
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); Narrator.skip(-1) }, enabled = st.segments.isNotEmpty()) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous sentence", tint = c.onHero)
                }
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); Narrator.skip(1) }, enabled = st.segments.isNotEmpty() && st.index < st.segments.lastIndex) {
                    Icon(Icons.Rounded.SkipNext, "Next sentence", tint = if (st.index < st.segments.lastIndex) c.onHero else dim.copy(alpha = 0.3f))
                }
                Spacer(Modifier.weight(1f))
                val rate = app.narratorRate
                Box(
                    Modifier.clip(CircleShape).border(1.dp, c.onHero.copy(alpha = 0.25f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val i = Narrator.SPEEDS.indexOfFirst { kotlin.math.abs(it - rate) < 0.01f }
                            Narrator.setRate(Narrator.SPEEDS[(i + 1) % Narrator.SPEEDS.size])
                        }
                        .semantics { contentDescription = "Playback speed ${speedLabel(rate)}" }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    AnimatedContent(rate, label = "rate") { r ->
                        Text(speedLabel(r), style = MaterialTheme.typography.labelLarge.merge(Tabular), color = c.onHero)
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Stop listening", tint = dim) }
            }
        }

        Box(Modifier.fillMaxWidth().height(3.dp).background(c.onHero.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(if (countdown != null) 1f else progress).fillMaxHeight().background(c.coral))
        }
    }
}

/** Voice + speed picker used from Profile settings. */
@Composable
fun VoicePickerDialog(onDismiss: () -> Unit) {
    val c = LA.colors
    val voices by Narrator.voices.collectAsStateWithLifecycle()
    val app by AppStore.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Narrator.warmUp() }
    DisposableEffect(Unit) { onDispose { if (Narrator.state.value.key == "preview") Narrator.stop() } }
    var selected by remember { mutableStateOf(Narrator.currentVoiceId()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Narrator voice", style = MaterialTheme.typography.headlineSmall, color = c.ink) },
        text = {
            Column {
                Text("Voices come from your phone, so they're free and most work offline. Tap one to hear it.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                Spacer(Modifier.height(12.dp))
                if (voices.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.coral)
                        Spacer(Modifier.width(12.dp))
                        Text("Finding voices…", color = c.inkSoft)
                    }
                } else Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    voices.forEach { v ->
                        val on = v.id == selected || (selected.isBlank() && v == voices.first())
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (on) c.cobaltSoft else Color.Transparent)
                                .clickable { selected = v.id; Narrator.preview(v.id) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(on, onClick = { selected = v.id; Narrator.preview(v.id) }, colors = RadioButtonDefaults.colors(selectedColor = c.cobalt))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.label, style = MaterialTheme.typography.titleSmall, color = c.ink)
                                Text(v.detail, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Speed")
                    Spacer(Modifier.weight(1f))
                    Text(speedLabel(app.narratorRate), style = MaterialTheme.typography.titleSmall.merge(Tabular), color = c.ink)
                }
                Slider(
                    value = app.narratorRate,
                    onValueChange = { v -> AppStore.update { it.copy(narratorRate = (Math.round(v * 20) / 20f)) } },
                    onValueChangeFinished = { Narrator.setRate(AppStore.state.value.narratorRate); Narrator.preview() },
                    valueRange = 0.75f..2f,
                    colors = SliderDefaults.colors(thumbColor = c.coral, activeTrackColor = c.coral, inactiveTrackColor = c.border),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = c.cobalt) } },
    )
}

