package com.rork.learnanything.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.learnanything.data.Visual
import com.rork.learnanything.ui.theme.LA
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Renders AI-requested visual explanations natively. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualView(visual: Visual, modifier: Modifier = Modifier, accent: Color = LA.colors.cobalt) {
    val c = LA.colors
    LACard(modifier.fillMaxWidth(), color = c.surface) {
        if (visual.title.isNotBlank()) {
            Text(visual.title, style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(4.dp))
        }
        if (visual.caption.isNotBlank()) {
            Text(visual.caption, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        val items = visual.items.take(8)
        when (visual.type) {
            "flow" -> FlowDiagram(visual, items, accent)
            "cycle" -> CycleDiagram(items.map { it.label to it.detail }, accent)
            "timeline" -> Column {
                items.forEachIndexed { i, it ->
                    Row(Modifier.staggerIn(i, visual, 70).height(IntrinsicSize.Min)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                            if (i < items.lastIndex) Box(Modifier.width(2.dp).fillMaxHeight().background(c.border))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Text(it.label, style = MaterialTheme.typography.titleMedium, color = c.ink)
                            if (it.detail.isNotBlank()) Text(it.detail, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                        }
                    }
                }
            }
            "bars" -> {
                val max = (items.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(0.0001f)
                var sel by remember(visual) { mutableIntStateOf(-1) }
                items.forEachIndexed { i, it ->
                    val anim = remember(visual) { Animatable(0f) }
                    LaunchedEffect(visual) { kotlinx.coroutines.delay(90L * i); anim.animateTo(1f, tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                    val isSel = sel == i
                    val barColor by animateColorAsState(if (isSel) c.coral else if (sel >= 0) accent.copy(alpha = 0.45f) else accent, label = "bar")
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .bouncyClick { sel = if (isSel) -1 else i }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Row {
                            Text(it.label, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                            Text(trimNum(it.value * anim.value).takeIf { anim.value < 1f } ?: trimNum(it.value), style = MaterialTheme.typography.labelLarge.merge(com.rork.learnanything.ui.theme.Tabular), color = if (isSel) c.coral else c.inkSoft)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(if (isSel) 14.dp else 10.dp).clip(CircleShape).background(c.surfaceAlt)) {
                            Box(Modifier.fillMaxWidth((it.value / max) * anim.value).fillMaxHeight().clip(CircleShape).background(barColor))
                        }
                        androidx.compose.animation.AnimatedVisibility(isSel) {
                            val share = items.sumOf { x -> x.value.toDouble() }.toFloat().takeIf { s -> s > 0f }
                            Text(
                                it.detail.ifBlank { "${trimNum(it.value)} · ${((it.value / max) * 100).toInt()}% of the largest" + (share?.let { s -> ", ${((it.value / s) * 100).toInt()}% of the total" } ?: "") },
                                style = MaterialTheme.typography.bodySmall, color = c.inkSoft, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (sel < 0) Text("Tap a bar for details", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 4.dp))
            }
            "graph" -> LineGraph(items.map { it.label to it.value }, accent)
            "formula" -> {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.coralSoft).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(visual.formula.ifBlank { items.firstOrNull()?.label ?: "" }, style = MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                items.filter { it.label != visual.formula }.forEachIndexed { i, it ->
                    Row(Modifier.staggerIn(i + 1, visual).padding(vertical = 3.dp)) {
                        Text(it.label, style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.widthIn(min = 40.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it.detail, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { // compare
                items.chunked(2).forEachIndexed { r, row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { k, it ->
                            Column(Modifier.weight(1f).fillMaxHeight().staggerIn(r * 2 + k, visual, 70).clip(RoundedCornerShape(14.dp)).background(c.surfaceAlt).padding(12.dp)) {
                                Text(it.label, style = MaterialTheme.typography.titleMedium, color = accent)
                                if (it.detail.isNotBlank()) Text(it.detail, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun trimNum(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowDiagram(visual: Visual, items: List<com.rork.learnanything.data.VisualItem>, accent: Color) {
    val c = LA.colors
    var sel by remember(visual) { mutableIntStateOf(-1) }
    // A travelling highlight walks the chain once, then hands control to the learner.
    var walk by remember(visual) { mutableIntStateOf(-1) }
    LaunchedEffect(visual) {
        kotlinx.coroutines.delay(500)
        for (i in items.indices) { if (sel >= 0) break; walk = i; kotlinx.coroutines.delay(420) }
        walk = -1
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, it ->
            Row(Modifier.staggerIn(i, visual, 60), verticalAlignment = Alignment.CenterVertically) {
                val on = sel == i || walk == i
                val last = i == items.lastIndex
                val bg by animateColorAsState(
                    when { sel == i -> accent; on -> accent.copy(alpha = 0.22f); last -> accent.copy(alpha = 0.14f); else -> c.surfaceAlt }, label = "fn",
                )
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(bg)
                        .bouncyClick { sel = if (sel == i) -1 else i }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge, color = if (sel == i) Color.White else if (last || on) accent else c.ink)
                }
                if (i < items.lastIndex) {
                    val tint by animateColorAsState(if (walk == i || sel == i) accent else c.inkFaint, label = "fa")
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = tint, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
        }
    }
    val shown = items.getOrNull(sel)
    androidx.compose.animation.AnimatedContent(shown, label = "fd") { item ->
        if (item == null) {
            Text("Tap a step to see what happens there", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 10.dp))
        } else {
            val i = items.indexOf(item)
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.08f)).padding(12.dp)) {
                Text("STEP ${i + 1} OF ${items.size}", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = accent)
                Spacer(Modifier.height(2.dp))
                Text(item.label, style = MaterialTheme.typography.titleMedium, color = c.ink)
                val next = items.getOrNull(i + 1)?.label
                val prev = items.getOrNull(i - 1)?.label
                Text(
                    item.detail.ifBlank { listOfNotNull(prev?.let { p -> "Comes from $p." }, next?.let { n -> "Leads to $n." } ?: "This is the end result.").joinToString(" ") },
                    style = MaterialTheme.typography.bodyMedium, color = c.inkSoft,
                )
            }
        }
    }
}

@Composable
private fun CycleDiagram(items: List<Pair<String, String>>, accent: Color) {
    val c = LA.colors
    var active by remember(items) { mutableIntStateOf(0) }
    var auto by remember(items) { mutableStateOf(true) }
    LaunchedEffect(items, auto) {
        while (auto && items.isNotEmpty()) { kotlinx.coroutines.delay(1700); active = (active + 1) % items.size }
    }
    val n = items.size.coerceAtLeast(1)
    val sweep by animateFloatAsState(360f / n * (active + 1), tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "sweep")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(104.dp).bouncyClick { auto = !auto }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 - 9.dp.toPx()
                val sw = 4.dp.toPx()
                drawCircle(c.border, r, style = Stroke(sw))
                drawArc(accent, -90f, sweep, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                items.indices.forEach { i ->
                    val a = -PI / 2 + i * 2 * PI / n
                    val o = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
                    drawCircle(c.surface, 8.dp.toPx(), o)
                    drawCircle(if (i == active) c.coral else if (i < active) accent else c.border, if (i == active) 7.dp.toPx() else 5.dp.toPx(), o)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${active + 1}/${items.size}", style = MaterialTheme.typography.titleMedium.merge(com.rork.learnanything.ui.theme.Tabular), color = c.ink)
                Icon(if (auto) Icons.Rounded.Loop else Icons.Rounded.Pause, null, tint = c.inkFaint, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, (label, detail) ->
                val on = i == active
                val bg by animateColorAsState(if (on) accent.copy(alpha = 0.09f) else Color.Transparent, label = "cr")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                        .bouncyClick { active = i; auto = false }.padding(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) c.coral else accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = if (on) Color.White else accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.titleMedium, color = if (on) c.ink else c.inkSoft)
                        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineGraph(points: List<Pair<String, Float>>, accent: Color) {
    val c = LA.colors
    val measurer = rememberTextMeasurer()
    val anim = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { anim.animateTo(1f, tween(1000)) }
    var scrub by remember(points) { mutableIntStateOf(-1) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val padPx = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    fun idxAt(x: Float): Int {
        if (points.size < 2) return -1
        val f = ((x - padPx) / (widthPx - padPx)).coerceIn(0f, 1f)
        return (f * (points.size - 1) + 0.5f).toInt().coerceIn(0, points.lastIndex)
    }
    val sel = points.getOrNull(scrub)
    Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sel != null) {
            Text(sel.first, style = MaterialTheme.typography.labelLarge, color = c.inkSoft)
            Spacer(Modifier.width(8.dp))
            Text(trimNum(sel.second), style = MaterialTheme.typography.titleMedium.merge(com.rork.learnanything.ui.theme.Tabular), color = c.coral)
        } else Text("Touch and drag across the graph", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
    }
    Spacer(Modifier.height(6.dp))
    Canvas(
        Modifier.fillMaxWidth().height(170.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(points) {
                detectTapGestures(onPress = { o -> scrub = idxAt(o.x); tryAwaitRelease() })
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(onDragEnd = {}, onHorizontalDrag = { ch, _ -> scrub = idxAt(ch.position.x) })
            }
    ) {
        if (points.size < 2) return@Canvas
        val pad = 24.dp.toPx()
        val w = size.width - pad; val h = size.height - pad
        val minV = points.minOf { it.second }; val maxV = points.maxOf { it.second }
        val span = (maxV - minV).takeIf { it > 0 } ?: 1f
        drawLine(c.border, Offset(pad, 0f), Offset(pad, h), 2f)
        drawLine(c.border, Offset(pad, h), Offset(size.width, h), 2f)
        val pts = points.mapIndexed { i, p -> Offset(pad + w * i / (points.size - 1), h - h * 0.9f * (p.second - minV) / span - h * 0.05f) }
        val path = Path()
        val count = (pts.size * anim.value).toInt().coerceAtLeast(1)
        pts.take(count).forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        pts.take(count).forEach { drawCircle(accent, 4.dp.toPx(), it) }
        if (scrub in pts.indices) {
            val p = pts[scrub]
            drawLine(c.inkFaint, Offset(p.x, 0f), Offset(p.x, h), 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            drawCircle(c.surface, 8.dp.toPx(), p)
            drawCircle(c.coral, 6.dp.toPx(), p)
        }
        val step = (points.size / 5).coerceAtLeast(1)
        points.forEachIndexed { i, p ->
            if (i % step == 0) {
                val layout = measurer.measure(p.first.take(6), TextStyle(fontSize = 10.sp, color = c.inkSoft))
                drawText(layout, topLeft = Offset(pts[i].x - layout.size.width / 2, h + 6.dp.toPx()))
            }
        }
    }
}

// ---------------- Engine simulation ----------------

fun strokeFor(angle: Float): Int = ((angle % 720f) / 180f).toInt().coerceIn(0, 3)
val STROKES = listOf("Intake", "Compression", "Power", "Exhaust")

fun cylinderPressure(angle: Float): Float {
    val a = angle % 720f
    val clearance = 1f / 9f // compression ratio 10:1
    val x = ((1 - cos(Math.toRadians((a % 360f).toDouble()))) / 2).toFloat()
    return when (strokeFor(a)) {
        0 -> 0.9f
        1 -> ((clearance + 1f) / (clearance + x)).pow(1.3f)
        2 -> 60f * (clearance / (clearance + x)).pow(1.3f)
        else -> 1.1f
    }
}

@Composable
fun EngineCylinder(angle: Float, modifier: Modifier = Modifier, line: Color = LA.colors.ink, showGas: Boolean = true) {
    val c = LA.colors
    val cobalt = c.cobalt; val coral = c.coral; val amber = c.amber
    Canvas(modifier) { drawEngine(angle, line, cobalt, coral, amber, showGas, c.surface) }
}

private fun DrawScope.drawEngine(angle: Float, line: Color, cobalt: Color, coral: Color, amber: Color, showGas: Boolean, fill: Color) {
    val w = size.width; val h = size.height
    val sw = (w * 0.008f).coerceAtLeast(2f)
    val a = angle % 720f
    val theta = Math.toRadians(a.toDouble())
    val r = h * 0.13f; val rod = h * 0.30f
    val cc = Offset(w * 0.5f, h * 0.82f)
    val pinY = cc.y - r * cos(theta).toFloat() - sqrt(rod * rod - (r * sin(theta).toFloat()).pow(2))
    val pistonTop = pinY - h * 0.07f
    val left = w * 0.30f; val right = w * 0.70f
    val headBottom = h * 0.24f
    val stroke = strokeFor(a)

    // gas
    if (showGas) {
        val gas = when (stroke) {
            0 -> cobalt.copy(alpha = 0.14f)
            1 -> cobalt.copy(alpha = 0.14f + 0.3f * ((a - 180f) / 180f))
            2 -> coral.copy(alpha = 0.55f - 0.4f * ((a - 360f) / 180f))
            else -> Color.Gray.copy(alpha = 0.22f)
        }
        drawRect(gas, Offset(left, headBottom), Size(right - left, pistonTop - headBottom))
    }
    // walls
    drawLine(line, Offset(left, headBottom - h * 0.02f), Offset(left, h * 0.70f), sw * 1.6f)
    drawLine(line, Offset(right, headBottom - h * 0.02f), Offset(right, h * 0.70f), sw * 1.6f)
    // head
    val head = Path().apply {
        moveTo(left - w * 0.06f, headBottom)
        lineTo(left, headBottom); lineTo(w * 0.40f, h * 0.17f); lineTo(w * 0.60f, h * 0.17f); lineTo(right, headBottom)
        lineTo(right + w * 0.06f, headBottom)
    }
    drawPath(head, line, style = Stroke(sw * 1.4f))
    // valves
    val intakeOpen = stroke == 0
    val exhaustOpen = stroke == 3
    fun valve(x: Float, dir: Float, open: Boolean) {
        val drop = if (open) h * 0.035f else 0f
        val top = Offset(x - dir * w * 0.06f, h * 0.02f + drop)
        val bot = Offset(x, h * 0.185f + drop)
        drawLine(line, top, bot, sw * 1.3f)
        drawLine(line, Offset(bot.x - w * 0.045f, bot.y), Offset(bot.x + w * 0.045f, bot.y), sw * 2.2f, cap = StrokeCap.Round)
        // spring
        for (i in 0..3) {
            val y = h * 0.05f + i * h * 0.018f + drop * 0.5f
            val cx = x - dir * w * 0.06f * (1 - (y - h * 0.02f) / (h * 0.165f))
            drawLine(line.copy(alpha = 0.6f), Offset(cx - w * 0.025f, y), Offset(cx + w * 0.025f, y + h * 0.008f), sw)
        }
        if (open && showGas) {
            val arrowC = if (dir < 0) cobalt else Color.Gray
            val ay = bot.y + h * 0.03f
            drawLine(arrowC, Offset(x, ay), Offset(x, ay + h * 0.05f), sw * 1.2f)
        }
    }
    valve(w * 0.40f, -1f, intakeOpen)
    valve(w * 0.60f, 1f, exhaustOpen)
    // spark plug
    drawRoundRect(line, Offset(w * 0.475f, h * 0.0f), Size(w * 0.05f, h * 0.15f), CornerRadius(4f), style = Stroke(sw * 1.2f))
    drawLine(line, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.18f), sw * 1.4f)
    if (a in 350f..395f) {
        for (k in 0 until 8) {
            val ang = k * PI / 4
            drawLine(amber, Offset(w * 0.5f, h * 0.19f), Offset(w * 0.5f + (w * 0.05f * cos(ang)).toFloat(), h * 0.19f + (w * 0.05f * sin(ang)).toFloat()), sw * 1.3f, cap = StrokeCap.Round)
        }
    }
    // piston
    val pw = right - left - sw * 3
    drawRoundRect(fill, Offset(left + sw * 1.5f, pistonTop), Size(pw, h * 0.13f), CornerRadius(6f))
    drawRoundRect(line, Offset(left + sw * 1.5f, pistonTop), Size(pw, h * 0.13f), CornerRadius(6f), style = Stroke(sw * 1.3f))
    for (i in 1..2) drawLine(line.copy(alpha = 0.5f), Offset(left + sw * 1.5f, pistonTop + i * h * 0.018f), Offset(right - sw * 1.5f, pistonTop + i * h * 0.018f), sw * 0.8f)
    drawCircle(line, h * 0.022f, Offset(w * 0.5f, pinY), style = Stroke(sw * 1.2f))
    // crank
    val crankPin = Offset(cc.x + r * sin(theta).toFloat(), cc.y - r * cos(theta).toFloat())
    drawLine(line, Offset(w * 0.5f, pinY), crankPin, sw * 3f, cap = StrokeCap.Round)
    drawCircle(line.copy(alpha = 0.35f), r * 1.15f, cc, style = Stroke(sw))
    drawLine(line, cc, crankPin, sw * 3.5f, cap = StrokeCap.Round)
    drawCircle(line, h * 0.02f, cc)
    drawCircle(coral, h * 0.016f, crankPin)
}

@Composable
fun SubjectIllustration(world: String, color: Color, accent: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = 2.dp.toPx()
        when (world) {
            "mechanics" -> drawEngine(250f, color, accent.copy(alpha = 0.7f), accent, accent, false, Color.Transparent)
            "math" -> {
                drawLine(color, Offset(w * 0.1f, h * 0.85f), Offset(w * 0.95f, h * 0.85f), sw)
                drawLine(color, Offset(w * 0.15f, h * 0.05f), Offset(w * 0.15f, h * 0.95f), sw)
                val p = Path()
                for (i in 0..40) {
                    val x = i / 40f
                    val y = 0.85f - 3.2f * (x - 0.55f).pow(2) * -1 - 0.0f
                    val yy = h * (0.15f + 3.0f * (x - 0.55f) * (x - 0.55f))
                    if (i == 0) p.moveTo(w * (0.15f + x * 0.8f), yy) else p.lineTo(w * (0.15f + x * 0.8f), yy)
                    if (y < -100) break
                }
                drawPath(p, accent, style = Stroke(sw * 1.4f, cap = StrokeCap.Round))
                drawCircle(accent, 4.dp.toPx(), Offset(w * (0.15f + 0.55f * 0.8f), h * 0.15f))
            }
            "business" -> {
                drawLine(color, Offset(w * 0.1f, h * 0.88f), Offset(w * 0.95f, h * 0.88f), sw)
                val vals = listOf(0.3f, 0.45f, 0.38f, 0.6f, 0.55f, 0.82f)
                vals.forEachIndexed { i, v ->
                    val x = w * (0.15f + i * 0.14f)
                    drawRoundRect(color.copy(alpha = 0.35f), Offset(x, h * (0.88f - v * 0.8f)), Size(w * 0.08f, h * v * 0.8f), CornerRadius(4f))
                }
                val p = Path()
                vals.forEachIndexed { i, v -> val o = Offset(w * (0.19f + i * 0.14f), h * (0.82f - v * 0.8f)); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                drawPath(p, accent, style = Stroke(sw * 1.4f, cap = StrokeCap.Round))
            }
            "programming" -> {
                val t = measurer.measure("</>", TextStyle(fontSize = (h / 3.2f).toSp(), color = color, fontWeight = FontWeight.Light))
                drawText(t, topLeft = Offset((w - t.size.width) / 2, (h - t.size.height) / 2))
                drawCircle(accent, 5.dp.toPx(), Offset(w * 0.82f, h * 0.2f))
            }
            "science", "general" -> {
                // incline with forces
                val base = Offset(w * 0.05f, h * 0.88f)
                val top = Offset(w * 0.95f, h * 0.30f)
                drawLine(color, base, top, sw)
                drawLine(color.copy(alpha = 0.5f), base, Offset(w * 0.95f, h * 0.88f), sw, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                val angle = kotlin.math.atan2(top.y - base.y, top.x - base.x)
                val bx = w * 0.55f; val by = base.y + (bx - base.x) * kotlin.math.tan(angle)
                val bs = h * 0.18f
                rotateAround(angle, Offset(bx, by)) { drawRect(color, Offset(bx - bs / 2, by - bs), Size(bs, bs), style = Stroke(sw)) }
                val cx = bx + (bs / 2) * sin(angle); val cy = by - (bs / 2) * cos(angle)
                drawLine(color, Offset(cx, cy), Offset(cx, h * 0.02f), sw); // N-ish
                drawLine(color, Offset(cx, cy), Offset(cx, h * 0.98f), sw)
                drawLine(accent, Offset(cx, cy), Offset(cx + w * 0.25f * cos(angle), cy + w * 0.25f * sin(angle)), sw * 1.5f)
                listOf("N" to Offset(cx + 6, h * 0.02f), "mg" to Offset(cx + 6, h * 0.86f), "F" to Offset(cx + w * 0.22f * cos(angle), cy + w * 0.25f * sin(angle) - 30)).forEach { (s, o) ->
                    drawText(measurer.measure(s, TextStyle(fontSize = 13.sp, color = color)), topLeft = o)
                }
            }
            else -> {
                drawCircle(color, h * 0.12f, center, style = Stroke(sw))
                drawOval(color.copy(alpha = 0.7f), Offset(w * 0.12f, h * 0.36f), Size(w * 0.76f, h * 0.28f), style = Stroke(sw))
                rotateAround(1.0f, center) { drawOval(color.copy(alpha = 0.7f), Offset(w * 0.12f, h * 0.36f), Size(w * 0.76f, h * 0.28f), style = Stroke(sw)) }
                drawCircle(accent, 5.dp.toPx(), Offset(w * 0.86f, h * 0.48f))
            }
        }
    }
}

private inline fun DrawScope.rotateAround(radians: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.rotate(Math.toDegrees(radians.toDouble()).toFloat(), pivot)
    block()
    drawContext.transform.rotate(-Math.toDegrees(radians.toDouble()).toFloat(), pivot)
}

@Composable
fun OutlinedDropBox(text: String?, highlighted: Boolean, modifier: Modifier = Modifier) {
    val c = LA.colors
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (text != null) c.cobaltSoft else c.surface.copy(alpha = 0.9f))
            .border(1.5.dp, if (highlighted) c.coral else if (text != null) c.cobalt else c.inkFaint, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text ?: "Drop here", style = MaterialTheme.typography.labelMedium, color = if (text != null) c.cobalt else c.inkFaint, maxLines = 1)
    }
}
