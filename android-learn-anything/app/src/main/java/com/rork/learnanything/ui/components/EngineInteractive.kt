package com.rork.learnanything.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Interactive four-stroke simulator: crank angle slider drives valves, piston, spark and pressure. */
@Composable
fun EngineSim(modifier: Modifier = Modifier, onSparkFound: () -> Unit = {}) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    var angle by remember { mutableFloatStateOf(214f) }
    var playing by remember { mutableStateOf(false) }
    var sparkFound by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        while (playing) { delay(16); angle = (angle + 3f) % 720f }
    }
    LaunchedEffect(angle) {
        if (!sparkFound && angle in 350f..395f) {
            sparkFound = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSparkFound()
        }
    }
    val stroke = strokeFor(angle)
    val pressure = cylinderPressure(angle)
    val strokeColors = listOf(c.cobalt.copy(alpha = 0.35f), c.coral.copy(alpha = 0.55f), c.amber.copy(alpha = 0.55f), c.inkFaint.copy(alpha = 0.45f))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LACard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                STROKES.forEachIndexed { i, s ->
                    val sel = i == stroke
                    Box(
                        Modifier.weight(1f).clip(CircleShape)
                            .background(if (sel) c.coral else Color.Transparent)
                            .border(1.dp, if (sel) c.coral else c.border, CircleShape)
                            .clickable(onClickLabel = "Jump to $s") { playing = false; angle = i * 180f + 90f }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(s, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else c.ink, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
                val w = maxWidth
                EngineCylinder(angle, Modifier.width(w * 0.62f).fillMaxHeight().align(Alignment.Center).semantics {
                    contentDescription = "Engine cylinder at ${angle.roundToInt()} degrees, ${STROKES[stroke]} stroke"
                })
                Column(
                    Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(12.dp)).background(c.cobaltSoft)
                        .border(1.dp, c.cobalt.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Pressure", fontSize = 11.sp, color = c.cobalt)
                    Text("%.1f bar".format(pressure), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
                }
                PartLabel("Spark plug", Modifier.align(Alignment.TopStart))
                PartLabel("Intake valve", Modifier.align(Alignment.CenterStart).offset(y = (-50).dp))
                PartLabel("Exhaust valve", Modifier.align(Alignment.CenterEnd).offset(y = (-50).dp))
                PartLabel("Piston", Modifier.align(Alignment.CenterEnd).offset(y = 30.dp))
                if (angle in 350f..395f) {
                    Text("Spark!", color = c.amber, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
        LACard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Crank angle ${angle.roundToInt()}°", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { playing = !playing }) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause engine" else "Run engine")
                }
            }
            Slider(
                value = angle, onValueChange = { playing = false; angle = it }, valueRange = 0f..719f,
                colors = SliderDefaults.colors(thumbColor = c.coral, activeTrackColor = c.coral, inactiveTrackColor = c.border),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("0°", fontSize = 11.sp, color = c.inkSoft, modifier = Modifier.weight(1f))
                Text("360°", fontSize = 11.sp, color = c.inkSoft, modifier = Modifier.weight(1f))
                Text("720°", fontSize = 11.sp, color = c.inkSoft)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                STROKES.forEachIndexed { i, s ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val alpha by animateFloatAsState(if (i == stroke) 1f else 0.5f, label = "seg")
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(strokeColors[i].copy(alpha = strokeColors[i].alpha * alpha + 0.1f)))
                        Spacer(Modifier.height(4.dp))
                        Text(s, fontSize = 11.sp, color = if (i == stroke) c.ink else c.inkSoft, fontWeight = if (i == stroke) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (sparkFound) "You found it: the spark fires near 360°, at the top of compression, kicking off the power stroke."
                else "Goal: find the exact moment the spark plug fires. Watch the pressure.",
                style = MaterialTheme.typography.bodyMedium, color = if (sparkFound) c.success else c.inkSoft,
            )
        }
    }
}

@Composable
private fun PartLabel(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, fontSize = 12.sp, color = LA.colors.inkSoft)
}

val ENGINE_PARTS = listOf("Intake valve", "Exhaust valve", "Spark plug", "Piston")

/**
 * Drag-and-drop labeling. Each chip can be dragged onto a target; tapping a chip then a target works too
 * (accessible fallback). Reports (ready, correct, answer) on every change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineLabelGame(locked: Boolean, onChange: (ready: Boolean, correct: Boolean, answer: String) -> Unit, modifier: Modifier = Modifier) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val assigned = remember { mutableStateMapOf<String, String>() } // target -> chip
    val chipCenters = remember { mutableStateMapOf<String, Offset>() }
    val targetRects = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<String?>(null) }
    val pool = remember { ENGINE_PARTS.shuffled() }

    fun report() {
        val ready = assigned.size == ENGINE_PARTS.size
        val correct = ENGINE_PARTS.all { assigned[it] == it }
        onChange(ready, correct, assigned.entries.joinToString { "${it.value} → ${it.key}" })
    }
    fun place(target: String, chip: String) {
        assigned.entries.firstOrNull { it.value == chip }?.let { assigned.remove(it.key) }
        assigned[target] = chip
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        report()
    }

    LACard(modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Column(Modifier.zIndex(if (dragging != null) 2f else 0f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                pool.filter { it !in assigned.values }.forEach { label ->
                    val isDrag = dragging == label
                    val sel = selected == label
                    Row(
                        Modifier
                            .onGloballyPositioned { chipCenters[label] = it.boundsInRoot().center }
                            .offset { if (isDrag) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else IntOffset.Zero }
                            .zIndex(if (isDrag) 3f else 0f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDrag || sel) c.cobaltSoft else c.surface)
                            .border(1.5.dp, if (isDrag || sel) c.cobalt else c.border, RoundedCornerShape(12.dp))
                            .pointerInput(label, locked) {
                                if (locked) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { dragging = label; dragOffset = Offset.Zero; selected = null },
                                    onDrag = { change, amount -> change.consume(); dragOffset += amount },
                                    onDragEnd = {
                                        val p = (chipCenters[label] ?: Offset.Zero) + dragOffset
                                        targetRects.entries.firstOrNull { it.value.inflate(30f).contains(p) }?.let { place(it.key, label) }
                                        dragging = null; dragOffset = Offset.Zero
                                    },
                                    onDragCancel = { dragging = null; dragOffset = Offset.Zero },
                                )
                            }
                            .clickable(enabled = !locked, onClickLabel = "Select $label") { selected = if (sel) null else label }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.DragIndicator, null, tint = c.inkFaint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (selected != null) "Now tap where \"$selected\" goes" else "Drag each part onto its box, or tap a part then a box.",
            style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
        )
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(290.dp)) {
            val w = maxWidth
            val canvasTop = 56.dp; val canvasH = 224.dp
            val faint = c.inkFaint
            // connector lines from boxes to parts
            Canvas(Modifier.fillMaxSize()) {
                val W = size.width
                val top = canvasTop.toPx(); val ch = canvasH.toPx()
                fun cx(f: Float) = W * 0.2f + W * 0.6f * f
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                drawLine(faint, Offset(W * 0.5f, 42.dp.toPx()), Offset(cx(0.5f), top + ch * 0.02f), 2f, pathEffect = dash)
                drawLine(faint, Offset(W * 0.30f, top + 22.dp.toPx()), Offset(cx(0.36f), top + ch * 0.10f), 2f, pathEffect = dash)
                drawLine(faint, Offset(W * 0.70f, top + 22.dp.toPx()), Offset(cx(0.64f), top + ch * 0.10f), 2f, pathEffect = dash)
                drawLine(faint, Offset(W * 0.72f, top + ch * 0.55f), Offset(cx(0.5f), top + ch * 0.58f), 2f, pathEffect = dash)
            }
            EngineCylinder(200f, Modifier.padding(top = canvasTop).width(w * 0.6f).height(canvasH).align(Alignment.TopCenter), showGas = false)
            val targets = listOf(
                "Spark plug" to Modifier.align(Alignment.TopCenter).width(w * 0.34f),
                "Intake valve" to Modifier.align(Alignment.TopStart).offset(y = canvasTop + 4.dp).width(w * 0.3f),
                "Exhaust valve" to Modifier.align(Alignment.TopEnd).offset(y = canvasTop + 4.dp).width(w * 0.3f),
                "Piston" to Modifier.align(Alignment.TopEnd).offset(y = canvasTop + canvasH * 0.55f - 18.dp).width(w * 0.28f),
            )
            targets.forEach { (target, mod) ->
                val value = assigned[target]
                val result = if (locked && value != null) (value == target) else null
                Box(
                    mod.height(38.dp)
                        .onGloballyPositioned { targetRects[target] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !locked, onClickLabel = "Place part here") {
                            val s = selected
                            if (s != null) { place(target, s); selected = null } else if (value != null) { assigned.remove(target); report() }
                        }
                ) {
                    OutlinedDropBox(
                        text = value,
                        highlighted = dragging != null || selected != null,
                        modifier = Modifier.fillMaxSize().then(
                            if (result == true) Modifier.border(2.dp, c.success, RoundedCornerShape(10.dp))
                            else if (result == false) Modifier.border(2.dp, c.coral, RoundedCornerShape(10.dp)) else Modifier
                        ),
                    )
                }
            }
        }
    }
}
