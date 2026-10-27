package com.rork.learnanything.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Course
import com.rork.learnanything.data.LessonRef
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.worldStyle

@Composable
fun PathsScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val course = s.courses.firstOrNull { it.id == s.activeCourseId } ?: s.courses.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lessonMenu by remember { mutableStateOf<LessonRef?>(null) }

    if (course == null) {
        Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding(), verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Rounded.Route, "No paths yet", "Tell me any goal and I'll build a personal path around what you already know.", action = "Build a path", onAction = { nav.navigate(Routes.intake()) })
        }
        return
    }

    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(course.title, style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.weight(1f).padding(top = 6.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Path options", tint = c.ink) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New path") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; nav.navigate(Routes.intake()) })
                        DropdownMenuItem(text = { Text("Ask tutor about this path") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) }, onClick = {
                            menu = false
                            AppStore.pendingTutorPrompt.value = "I'm learning \"${course.title}\". Give me the big picture in a few lines and tell me what matters most."
                            nav.navigate(Routes.TUTOR) { launchSingleTop = true }
                        })
                        DropdownMenuItem(text = { Text("Final assessment") }, leadingIcon = { Icon(Icons.Rounded.EmojiEvents, null) }, onClick = { menu = false; nav.navigate(Routes.quiz("mock", course.title)) })
                        DropdownMenuItem(text = { Text("Delete path", color = c.coral) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.coral) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val anim by androidx.compose.animation.core.animateFloatAsState(course.progress, label = "p")
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(c.border)) {
                Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(CircleShape).background(c.coral))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                StatText(course.doneCount.toString(), " of ${course.total} mastered", Modifier.weight(1f))
                StatText(course.skippedCount.toString(), " skipped", Modifier.weight(0.7f))
                StatText(minutesLabel(course.minutesLeft), " left", prefix = "About ")
            }
        }

        if (s.courses.size > 1) {
            Spacer(Modifier.height(14.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.courses, key = { it.id }) { co ->
                    val ws = worldStyle(co.world)
                    FilterChip(
                        selected = co.id == course.id, onClick = { AppStore.setActiveCourse(co.id) },
                        label = { Text(co.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                        leadingIcon = { Icon(ws.icon, null, Modifier.size(16.dp), tint = ws.accent) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            if (course.adaptiveNote.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(c.cobaltSoft)
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(c.cobalt))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SparkleMark(Modifier.size(22.dp), c.cobalt)
                        Spacer(Modifier.width(14.dp))
                        Text(course.adaptiveNote, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                }
            }
            val current = course.currentLesson?.id
            course.modules.forEachIndexed { mi, m ->
                Spacer(Modifier.height(20.dp))
                Text("MODULE ${mi + 1} · ${m.title.uppercase()}", fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold, color = c.inkSoft)
                Spacer(Modifier.height(8.dp))
                m.lessons.forEachIndexed { li, l ->
                    val isLastInModule = li == m.lessons.lastIndex
                    val next = m.lessons.getOrNull(li + 1)
                    LessonNode(
                        l, isCurrent = l.id == current, showLine = !isLastInModule,
                        lineSolid = l.status == "done" && next?.status == "done",
                        onClick = { AppStore.setActiveCourse(course.id); nav.navigate(Routes.lesson(course.id, l.id)) },
                        onLongClick = { lessonMenu = l },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PathFooter(course, nav)
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this path?") },
        text = { Text("\"${course.title}\" and its progress will be removed from this device.") },
        confirmButton = { TextButton(onClick = { AppStore.deleteCourse(course.id); confirmDelete = false }) { Text("Delete", color = c.coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    lessonMenu?.let { l ->
        AlertDialog(
            onDismissRequest = { lessonMenu = null },
            title = { Text(l.title) },
            text = { Text("Adjust this lesson in your path.") },
            confirmButton = {
                TextButton(onClick = {
                    AppStore.mapLesson(course.id, l.id) { it.copy(status = if (it.status == "skipped") "upcoming" else "skipped") }
                    AppStore.update { st -> st.copy(courses = st.courses.map { co -> if (co.id == course.id && l.status != "skipped") co.copy(adaptiveNote = "Adapted for you: you know ${l.title.lowercase()}, so we skipped it.") else co }) }
                    lessonMenu = null
                }) { Text(if (l.status == "skipped") "Un-skip" else "I know this, skip it") }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppStore.mapLesson(course.id, l.id) { it.copy(status = "review", note = "extra practice") }
                    lessonMenu = null
                }) { Text("Review again") }
            },
        )
    }
}

@Composable
private fun StatText(value: String, rest: String, modifier: Modifier = Modifier, prefix: String = "") {
    val c = LA.colors
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(prefix)
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)); append(value); pop()
            append(rest)
        },
        modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LessonNode(l: LessonRef, isCurrent: Boolean, showLine: Boolean, lineSolid: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LA.colors
    val subtitle = when {
        l.status == "skipped" -> "skipped"
        l.status == "review" -> "${l.note.ifBlank { "extra practice" }} · ${l.minutes} min"
        l.status == "done" && l.accuracy >= 0 -> "${l.minutes} min · ${(l.accuracy * 100).toInt()}% correct"
        else -> "${l.minutes} min"
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(44.dp).fillMaxHeight()) {
            val green = c.success; val faint = c.inkFaint
            Canvas(Modifier.fillMaxSize()) {
                if (showLine) {
                    val x = size.width / 2
                    drawLine(
                        if (lineSolid) green else faint.copy(alpha = 0.6f), Offset(x, 40.dp.toPx()), Offset(x, size.height + 8.dp.toPx()), 2.dp.toPx(),
                        pathEffect = if (lineSolid) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }
            }
            Box(Modifier.padding(top = 18.dp).align(Alignment.TopCenter)) { NodeDot(l.status, isCurrent) }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp))
                .background(if (isCurrent) c.coralSoft else c.surface)
                .border(1.dp, if (isCurrent) c.coral.copy(alpha = 0.3f) else c.border, RoundedCornerShape(18.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Lesson options")
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(l.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp), color = if (l.status == "skipped") c.inkSoft else c.ink)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            }
            if (l.remedial) Pill("Strengthen", bg = c.cobaltSoft, fg = c.cobalt)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
        }
    }
}

@Composable
private fun NodeDot(status: String, current: Boolean) {
    val c = LA.colors
    val size = 30.dp
    when {
        status == "done" -> Box(Modifier.size(size).clip(CircleShape).background(c.success), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Done", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        current -> Box(Modifier.size(size).clip(CircleShape).background(c.coral), contentAlignment = Alignment.Center) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White))
        }
        status == "skipped" -> {
            val faint = c.inkFaint
            Canvas(Modifier.size(size)) {
                drawCircle(faint, this.size.minDimension / 2 - 2.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))))
                drawLine(faint, Offset(this.size.width * 0.38f, center.y), Offset(this.size.width * 0.62f, center.y), 2.dp.toPx())
            }
        }
        status == "review" -> Box(Modifier.size(size).clip(CircleShape).border(2.5.dp, c.cobalt, CircleShape).background(c.surface))
        else -> Box(Modifier.size(size).clip(CircleShape).border(2.dp, c.inkFaint, CircleShape).background(c.surface))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathFooter(course: Course, nav: NavController) {
    val c = LA.colors
    if (course.isComplete) {
        LACard(Modifier.fillMaxWidth(), color = c.successSoft, borderColor = c.success.copy(alpha = 0.3f)) {
            Text("Path complete", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Text("Prove it with a final assessment, then pick what's next.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Take final assessment", { nav.navigate(Routes.quiz("mock", course.title)) }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
    }
    if (course.prerequisites.isNotEmpty()) {
        Text("Check your foundations", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Text("Quick quizzes to catch gaps before they slow you down.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            course.prerequisites.forEach { p -> AssistChip(onClick = { nav.navigate(Routes.quiz("quiz", p)) }, label = { Text(p) }, leadingIcon = { Icon(Icons.Rounded.Quiz, null, Modifier.size(16.dp)) }) }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (course.nextSteps.isNotEmpty()) {
        Text("What to learn next", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            course.nextSteps.forEach { p -> AssistChip(onClick = { nav.navigate(Routes.intake(p)) }, label = { Text(p) }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) }) }
        }
    }
}
