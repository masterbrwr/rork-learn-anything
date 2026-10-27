package com.rork.learnanything.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.learnanything.data.LessonStep
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.Serif
import com.rork.learnanything.ui.theme.Tabular
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================================
// Motion helpers
// ============================================================================================

/** Fades and lifts content in, staggered by [index]. Replays when [key] changes. */
fun Modifier.staggerIn(index: Int, key: Any? = Unit, delayMs: Long = 45): Modifier = composed {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs * index.coerceIn(0, 12))
        a.animateTo(1f, tween(340, easing = LinearOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 18.dp.toPx()
    }
}

/** Subtle press-down scale for tappable surfaces. Pair with the same [source] on clickable. */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Clickable with press-scale feedback built in. */
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    this.pressScale(src).clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

/** Horizontal shake whenever [trigger] increases above zero. */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        for (v in listOf(-10f, 9f, -7f, 5f, -3f, 0f)) x.animateTo(v, tween(55))
    }
    graphicsLayer { translationX = x.value * density }
}

/** Radiating geometric burst: short lines flying out from the centre and fading. */
@Composable
fun Burst(play: Boolean, modifier: Modifier = Modifier, rays: Int = 14, key: Any? = Unit) {
    val c = LA.colors
    val colors = listOf(c.coral, c.cobalt, c.success, c.amber)
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(play, key) {
        if (play) { t.snapTo(0f); t.animateTo(1f, tween(720, easing = FastOutSlowInEasing)) }
    }
    if (!play || t.value <= 0f || t.value >= 1f) return
    Canvas(modifier) {
        val p = t.value
        val maxR = size.minDimension / 2
        for (i in 0 until rays) {
            val ang = i * 2 * PI / rays + (if (i % 2 == 0) 0.0 else PI / rays * 0.4)
            val reach = if (i % 2 == 0) 1f else 0.78f
            val start = maxR * reach * (0.38f + 0.5f * p)
            val len = maxR * 0.24f * (1f - p)
            val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
            drawLine(
                colors[i % colors.size].copy(alpha = (1f - p).coerceIn(0f, 1f)),
                Offset(center.x + dx * start, center.y + dy * start),
                Offset(center.x + dx * (start + len), center.y + dy * (start + len)),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
}

// ============================================================================================
// Estimate: slide to guess a number, then see how close you were
// ============================================================================================

data class EstimateSpec(val min: Float, val max: Float, val answer: Float, val tolerance: Float, val unit: String, val decimals: Int)

fun LessonStep.estimateSpec(): EstimateSpec {
    val ans = answerText.replace(",", "").trim().toFloatOrNull() ?: ((min + max) / 2f)
    var lo = min; var hi = max
    if (hi <= lo) { lo = 0f; hi = if (ans > 0f) niceCeil(ans * 2f) else 100f }
    if (ans < lo) lo = ans
    if (ans > hi) hi = ans
    val span = (hi - lo).coerceAtLeast(0.0001f)
    val tol = if (tolerance > 0f) tolerance else span * 0.08f
    val decimals = when { span <= 2f -> 2; span <= 20f -> 1; else -> 0 }
    return EstimateSpec(lo, hi, ans, tol, unit.trim(), decimals)
}

private fun niceCeil(v: Float): Float {
    if (v <= 10f) return 10f
    var m = 1f
    while (m * 10f < v) m *= 10f
    return (kotlin.math.ceil(v / m) * m)
}

fun EstimateSpec.format(v: Float): String {
    val core = when (decimals) {
        0 -> {
            val n = v.roundToInt()
            if (abs(n) >= 10000) "%,d".format(n) else n.toString()
        }
        1 -> "%.1f".format(v).removeSuffix(".0")
        else -> "%.2f".format(v)
    }
    return when {
        unit.isEmpty() -> core
        unit == "%" || unit == "°" || unit.startsWith("°") -> core + unit
        unit.length <= 1 && !unit[0].isLetter() -> unit + core
        else -> "$core $unit"
    }
}

fun LessonStep.estimateAnswerText(): String = estimateSpec().let { s -> "${s.format(s.answer)} (give or take ${s.format(s.tolerance)})" }

@Composable
fun EstimateStep(step: LessonStep, locked: Boolean, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val spec = remember(step) { step.estimateSpec() }
    var value by remember(step) { mutableFloatStateOf(spec.min + (spec.max - spec.min) / 2f) }
    var touched by remember(step) { mutableStateOf(false) }
    var lastTick by remember(step) { mutableIntStateOf(-1) }
    val off = abs(value - spec.answer)
    val ok = off <= spec.tolerance

    val numColor by animateColorAsState(
        when { locked && ok -> c.success; locked -> c.coral; touched -> c.ink; else -> c.inkFaint }, label = "num",
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (locked) "YOUR GUESS" else if (touched) "YOUR GUESS" else "SLIDE TO GUESS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = c.inkSoft)
        Spacer(Modifier.height(4.dp))
        Text(
            spec.format(value),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Serif, fontSize = 46.sp, lineHeight = 52.sp).merge(Tabular),
            color = numColor,
        )
        Spacer(Modifier.height(10.dp))
        if (!locked) {
            Slider(
                value = value,
                onValueChange = {
                    value = it; touched = true
                    val tick = (((it - spec.min) / (spec.max - spec.min)) * 20).toInt()
                    if (tick != lastTick) { lastTick = tick; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    onChange(true, abs(it - spec.answer) <= spec.tolerance, spec.format(it))
                },
                valueRange = spec.min..spec.max,
                colors = SliderDefaults.colors(thumbColor = c.cobalt, activeTrackColor = c.cobalt, inactiveTrackColor = c.border),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(spec.format(spec.min), style = MaterialTheme.typography.labelMedium, color = c.inkFaint, modifier = Modifier.weight(1f))
                Text(spec.format(spec.max), style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
            }
        } else {
            EstimateReveal(spec, value, ok)
        }
    }
}

@Composable
private fun EstimateReveal(spec: EstimateSpec, guess: Float, ok: Boolean) {
    val c = LA.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val span = (spec.max - spec.min).coerceAtLeast(0.0001f)
    fun frac(v: Float) = ((v - spec.min) / span).coerceIn(0f, 1f)
    val you = if (ok) c.success else c.coral
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val y = size.height * 0.55f
        val w = size.width
        val track = 8.dp.toPx()
        drawRoundRect(c.surfaceAlt, Offset(0f, y - track / 2), Size(w, track), CornerRadius(track))
        val bandL = frac(spec.answer - spec.tolerance) * w
        val bandR = frac(spec.answer + spec.tolerance) * w
        val p = anim.value
        val mid = frac(spec.answer) * w
        drawRoundRect(
            c.success.copy(alpha = 0.22f),
            Offset(mid - (mid - bandL) * p, y - track), Size(((bandR - bandL) * p).coerceAtLeast(2f), track * 2), CornerRadius(track),
        )
        // you marker
        val gx = frac(guess) * w
        drawLine(you, Offset(gx, y - 18.dp.toPx()), Offset(gx, y + 18.dp.toPx()), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(you, 6.dp.toPx(), Offset(gx, y - 18.dp.toPx()))
        // actual marker slides in from the guess
        val ax = gx + (mid - gx) * p
        drawLine(c.ink, Offset(ax, y - 12.dp.toPx()), Offset(ax, y + 12.dp.toPx()), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(c.ink, 5.dp.toPx(), Offset(ax, y + 14.dp.toPx()))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(you, "You  ${spec.format(guess)}")
        LegendDot(c.ink, "Actual  ${spec.format(spec.answer)}")
    }
    Spacer(Modifier.height(8.dp))
    val off = abs(guess - spec.answer)
    Text(
        if (off < spec.tolerance * 0.15f) "Spot on." else if (ok) "Within range, off by ${spec.format(off)}." else "Off by ${spec.format(off)}. The green band is close enough.",
        style = MaterialTheme.typography.bodyMedium, color = c.inkSoft, textAlign = TextAlign.Center,
    )
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.merge(Tabular), color = LA.colors.ink)
    }
}

// ============================================================================================
// Sort: tap an item, then the group it belongs to
// ============================================================================================

fun LessonStep.sortBuckets(): List<String> = options.filter { it.isNotBlank() }.ifEmpty { pairs.map { it.right }.distinct() }

private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortStep(step: LessonStep, locked: Boolean, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val buckets = remember(step) { step.sortBuckets() }
    val order = remember(step) { step.pairs.indices.shuffled() }
    val placed = remember(step) { mutableStateMapOf<Int, String>() }
    var sel by remember(step) { mutableIntStateOf(order.firstOrNull() ?: -1) }
    val bucketColors = listOf(c.cobalt, c.coral, c.success)

    fun report() {
        val ready = placed.size == step.pairs.size
        val ok = step.pairs.indices.all { i -> placed[i]?.let { same(it, step.pairs[i].right) } == true }
        onChange(ready, ok, step.pairs.indices.joinToString { i -> "${step.pairs[i].left} → ${placed[i] ?: "?"}" })
    }

    Text(
        if (locked) "Green is right, coral is in the wrong group." else "Tap an item, then the group it belongs in. Tap a placed item to take it back.",
        style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
    )
    Spacer(Modifier.height(12.dp))
    val pool = order.filter { it !in placed.keys }
    AnimatedVisibility(pool.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceAlt).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pool.forEachIndexed { k, i ->
                val active = sel == i
                val bg by animateColorAsState(if (active) c.cobalt else c.surface, label = "chip")
                Box(
                    Modifier.staggerIn(k, step).clip(RoundedCornerShape(12.dp)).background(bg)
                        .border(1.dp, if (active) c.cobalt else c.border, RoundedCornerShape(12.dp))
                        .bouncyClick(enabled = !locked) { sel = i; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) { Text(step.pairs[i].left, style = MaterialTheme.typography.labelLarge, color = if (active) Color.White else c.ink) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        buckets.forEachIndexed { b, name ->
            val col = bucketColors[b % bucketColors.size]
            val canDrop = sel >= 0 && !locked
            val border by animateColorAsState(if (canDrop) col else c.border, label = "bucket")
            Column(
                Modifier.weight(1f).fillMaxHeight().heightIn(min = 150.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (canDrop) col.copy(alpha = 0.06f) else c.surface)
                    .border(1.5.dp, border, RoundedCornerShape(18.dp))
                    .bouncyClick(enabled = canDrop) {
                        placed[sel] = name
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        sel = order.firstOrNull { it !in placed.keys } ?: -1
                        report()
                    }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2)
                }
                val here = order.filter { placed[it] == name }
                if (here.isEmpty()) Text(if (canDrop) "Tap to place" else "Empty", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
                here.forEach { i ->
                    val right = same(name, step.pairs[i].right)
                    val edge = when { !locked -> col.copy(alpha = 0.5f); right -> c.success; else -> c.coral }
                    key(i) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (locked) (if (right) c.successSoft else c.coralSoft) else c.surface)
                                .border(1.dp, edge, RoundedCornerShape(10.dp))
                                .bouncyClick(enabled = !locked) { placed.remove(i); sel = i; report() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(step.pairs[i].left, style = MaterialTheme.typography.labelLarge, color = c.ink, modifier = Modifier.weight(1f))
                            if (locked) Icon(if (right) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = edge, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Reveal: flip cards to self-test (ungraded)
// ============================================================================================

@Composable
fun RevealStep(step: LessonStep) {
    val c = LA.colors
    val flipped = remember(step) { mutableStateListOf<Int>() }
    val n = step.pairs.size
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.TouchApp, null, tint = c.inkSoft, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Guess first, then tap to flip", style = MaterialTheme.typography.bodySmall, color = c.inkSoft, modifier = Modifier.weight(1f))
        Text("${flipped.size} of $n", style = MaterialTheme.typography.labelLarge.merge(Tabular), color = if (flipped.size == n) c.success else c.ink)
    }
    Spacer(Modifier.height(8.dp))
    val prog by animateFloatAsState(if (n == 0) 0f else flipped.size / n.toFloat(), label = "rev")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.border)) {
        Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(CircleShape).background(if (flipped.size == n) c.success else c.cobalt))
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        step.pairs.withIndex().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (i, p) ->
                    FlipCard(
                        front = p.left, back = p.right, flipped = i in flipped,
                        modifier = Modifier.weight(1f).staggerIn(i, step),
                    ) { if (i in flipped) flipped.remove(i) else flipped.add(i) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FlipCard(front: String, back: String, flipped: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(520, easing = FastOutSlowInEasing), label = "flip")
    val showBack = rot > 90f
    Box(
        modifier.height(148.dp)
            .graphicsLayer { rotationY = rot; cameraDistance = 14f * density }
            .clip(RoundedCornerShape(18.dp))
            .background(if (showBack) c.hero else c.surface)
            .border(1.dp, if (showBack) c.hero else c.border, RoundedCornerShape(18.dp))
            .bouncyClick { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(14.dp),
    ) {
        if (!showBack) {
            Text(front, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.align(Alignment.CenterStart))
            Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                Text("?", style = MaterialTheme.typography.labelMedium, color = c.inkSoft, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                Text(front.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = c.coral, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(back, style = MaterialTheme.typography.bodyMedium, color = c.onHero)
            }
        }
    }
}

// ============================================================================================
// Tap words: pick the right words inside a sentence
// ============================================================================================

private fun normWord(s: String) = s.lowercase().trim { !it.isLetterOrDigit() }

fun LessonStep.tapTargets(): Set<String> = items.map(::normWord).filter { it.isNotEmpty() }.toSet()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TapWordsStep(step: LessonStep, locked: Boolean, onChange: (Boolean, Boolean, String) -> Unit) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val tokens = remember(step) { step.body.split(Regex("\\s+")).filter { it.isNotBlank() } }
    val target = remember(step) { step.tapTargets() }
    val sel = remember(step) { mutableStateListOf<Int>() }

    fun report() {
        val words = sel.map { normWord(tokens[it]) }.toSet()
        onChange(sel.isNotEmpty(), words == target, sel.sorted().joinToString { tokens[it].trim { ch -> !ch.isLetterOrDigit() } })
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tokens.forEachIndexed { i, t ->
                val w = normWord(t)
                val isSel = i in sel
                val isTarget = w in target
                val (bg, fg, edge) = when {
                    locked && isSel && isTarget -> Triple(c.successSoft, c.success, c.success)
                    locked && isSel -> Triple(c.coralSoft, c.coral, c.coral)
                    locked && isTarget -> Triple(Color.Transparent, c.success, c.success)
                    isSel -> Triple(c.cobalt, Color.White, c.cobalt)
                    else -> Triple(Color.Transparent, c.ink, Color.Transparent)
                }
                val animBg by animateColorAsState(bg, label = "tw")
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(animBg)
                        .border(1.dp, edge, RoundedCornerShape(8.dp))
                        .bouncyClick(enabled = !locked && w.isNotEmpty()) {
                            if (isSel) sel.remove(i) else sel.add(i)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            report()
                        }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) { Text(t, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal), color = fg) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(target.size.coerceAtMost(8)) { k ->
            val filled = k < sel.size
            val col by animateColorAsState(if (filled) c.cobalt else c.border, label = "dot")
            Box(Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(col))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (locked) "Green marks the words you were looking for." else "${sel.size} selected · find ${target.size}",
            style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
        )
    }
}

/** Small animated badge used for the answer streak. */
@Composable
fun ComboBadge(streak: Int, modifier: Modifier = Modifier) {
    val c = LA.colors
    AnimatedVisibility(streak >= 2, modifier = modifier, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) {
        val pulse = remember { Animatable(1f) }
        LaunchedEffect(streak) { pulse.snapTo(1.25f); pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        Row(
            Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                .clip(CircleShape).background(c.hero).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(10.dp)) {
                // three rising bars
                val bw = size.width / 4
                listOf(0.45f, 0.7f, 1f).forEachIndexed { k, h ->
                    drawRoundRect(c.coral, Offset(k * bw * 1.5f, size.height * (1 - h)), Size(bw, size.height * h), CornerRadius(bw / 2))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text("$streak in a row", style = MaterialTheme.typography.labelMedium.merge(Tabular), color = c.onHero)
        }
    }
}
