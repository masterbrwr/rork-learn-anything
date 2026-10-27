package com.rork.learnanything.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.OverlineStyle
import com.rork.learnanything.ui.theme.Tabular
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

/** Subtle press-down scale for tappable surfaces. Respects interaction source for accessibility. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, label: String? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return this
        .scale(scale)
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClickLabel = label, onClick = onClick)
}

@Composable
fun LACard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = LA.colors.surface,
    borderColor: Color = LA.colors.border,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(color)
        .border(BorderStroke(1.dp, borderColor), shape)
    Column(
        modifier = (if (onClick != null) base.pressable(onClick) else base).padding(padding),
        content = content,
    )
}

/** Small tracked uppercase label (eyebrow) for sections and metrics. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = LA.colors.inkSoft) {
    Text(text.uppercase(), style = OverlineStyle, color = color, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = LA.colors.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = LA.colors.cobalt, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    color: Color = LA.colors.coral,
    track: Color = LA.colors.border,
    label: String? = null,
    labelColor: Color = LA.colors.ink,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size).semantics { contentDescription = "${(progress * 100).toInt()} percent" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            drawArc(color, -90f, 360f * animated, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, color = labelColor, fontWeight = FontWeight.SemiBold, fontSize = (size.value / 4.2f).sp, style = Tabular)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, bg: Color = LA.colors.surfaceAlt, fg: Color = LA.colors.ink) {
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    color: Color = LA.colors.coral,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "btn")
    Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        enabled = enabled && !loading,
        interactionSource = source,
        modifier = modifier.heightIn(min = 54.dp).scale(scale),
        elevation = null,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = LA.colors.border, disabledContentColor = LA.colors.inkFaint),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = LA.colors.cobalt, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "btn2")
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.heightIn(min = 54.dp).scale(scale),
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.55f) else LA.colors.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = LA.colors.surface, contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Never leave the learner stuck: every failure offers retry, back and home. */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    message: String = "Check your connection and try again. Your progress is saved.",
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(LA.colors.coralSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudOff, null, tint = LA.colors.coral, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Something didn't load.", style = MaterialTheme.typography.headlineSmall, color = LA.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = LA.colors.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        if (onRetry != null) PrimaryButton("Retry", onRetry, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) SecondaryButton("Go back", onBack, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ArrowBack, color = LA.colors.inkSoft)
            if (onHome != null) SecondaryButton("Home", onHome, Modifier.weight(1f), icon = Icons.Rounded.Home, color = LA.colors.inkSoft)
        }
    }
}

@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-400f, 1200f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val base = LA.colors.surfaceAlt
    val hi = if (LA.colors.isDark) Color(0xFF2A3040) else Color(0xFFFBF9F5)
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 400f, 200f))
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(14.dp)) {
    Box(modifier.clip(shape).background(shimmerBrush()))
}

/** Skeleton loading with rotating status lines so generation never feels like a blank screen. */
@Composable
fun GeneratingState(statuses: List<String>, modifier: Modifier = Modifier) {
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(statuses) {
        while (true) { delay(1800); idx = (idx + 1) % statuses.size }
    }
    Column(modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SparkleMark(Modifier.size(22.dp), LA.colors.cobalt, animated = true)
            Spacer(Modifier.width(10.dp))
            androidx.compose.animation.AnimatedContent(targetState = statuses[idx], label = "status") { s ->
                Text(s, style = MaterialTheme.typography.titleMedium, color = LA.colors.ink)
            }
        }
        Spacer(Modifier.height(22.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(20.dp))
        Spacer(Modifier.height(16.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(22.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(16.dp))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(16.dp))
        Spacer(Modifier.height(20.dp))
        repeat(3) {
            SkeletonBlock(Modifier.fillMaxWidth().height(58.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun SparkleMark(modifier: Modifier = Modifier, color: Color = LA.colors.cobalt, animated: Boolean = false) {
    val rot = if (animated) {
        val t = rememberInfiniteTransition(label = "sparkle")
        t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s").value
    } else 1f
    Canvas(modifier.scale(rot)) {
        val w = size.width; val h = size.height
        fun star(cx: Float, cy: Float, r: Float) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx, cy, cx + r, cy)
                quadraticTo(cx, cy, cx, cy + r)
                quadraticTo(cx, cy, cx - r, cy)
                quadraticTo(cx, cy, cx, cy - r)
                close()
            }
            drawPath(p, color)
        }
        star(w * 0.42f, h * 0.55f, w * 0.40f)
        star(w * 0.82f, h * 0.18f, w * 0.17f)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp).clip(CircleShape).border(1.dp, LA.colors.border, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(LA.colors.cobaltSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = LA.colors.cobalt, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = LA.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LA.colors.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(action, onAction)
        }
    }
}

/** Light markdown: **bold**, ==highlight==, and "- " bullets. */
fun richText(text: String, bold: Color, highlight: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { li, rawLine ->
        var line = rawLine
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            append("•  ")
            line = line.trimStart().drop(2)
        }
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end > 0) { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = bold)) { append(line.substring(i + 2, end)) }; i = end + 2 } else { append(line[i]); i++ }
                }
                line.startsWith("==", i) -> {
                    val end = line.indexOf("==", i + 2)
                    if (end > 0) { withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Medium)) { append(line.substring(i + 2, end)) }; i = end + 2 } else { append(line[i]); i++ }
                }
                else -> { append(line[i]); i++ }
            }
        }
        if (li < lines.lastIndex) append("\n")
    }
}

@Composable
fun RichText(text: String, modifier: Modifier = Modifier, color: Color = LA.colors.ink, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    val hl = LA.colors.amberSoft
    Text(richText(text, color, hl), modifier = modifier, color = color, style = style)
}

@Composable
fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = LA.colors.ink) {
    LACard(modifier, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = accent, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Overline(label)
    }
}
