package com.rork.learnanything.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.*
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun dayLabel(day: Long): String {
    val f = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return f.format(Date(day * 86_400_000L))
}

private fun planFrom(payload: ExamPlanPayload) = payload.days.mapIndexed { i, d ->
    ExamDay(i, d.label.ifBlank { "Day ${i + 1}" }, d.focus, d.tasks.map { ExamTask(newId(), it.text, false, it.kind) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamScreen(nav: NavController, examId: String) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val exam = s.exams.firstOrNull { it.id == examId }
    if (examId.isNotBlank() && exam != null) ExamDetail(nav, exam) else ExamList(nav)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExamList(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(s.exams.isEmpty()) }
    var subject by rememberSaveable { mutableStateOf("") }
    var topics by rememberSaveable { mutableStateOf("") }
    var examDay by rememberSaveable { mutableLongStateOf(today() + 7) }
    var picker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun create() {
        busy = true; error = false
        scope.launch {
            val daysLeft = (examDay - today()).toInt().coerceAtLeast(1)
            runCatching { Ai.examPlan(subject, topics, daysLeft, AppStore.learnerContext()) }
                .onSuccess { p ->
                    val e = Exam(newId(), subject.trim(), "${subject.trim()} exam", examDay, topics, p.keyConcepts, planFrom(p), today())
                    AppStore.update { it.copy(exams = listOf(e) + it.exams) }
                    AppStore.track("exam_created", mapOf("subject" to subject))
                    creating = false; subject = ""; topics = ""
                    nav.navigate(Routes.exam(e.id))
                }
                .onFailure { AppStore.logError("examPlan", it); error = true }
            busy = false
        }
    }

    DetailScaffold(title = "Exam prep", onBack = { nav.popBackStack() }, actions = {
        if (!creating) IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "New exam") }
    }) { inner ->
        if (creating) {
            Column(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp)) {
                Text("Plan for an exam", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Text("I'll build a day-by-day plan with practice and mock tests, weighted to your weak spots.", color = c.inkSoft)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text("Subject") }, placeholder = { Text("e.g. Biology final") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(topics, { topics = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp), label = { Text("Topics (optional)") }, placeholder = { Text("Cell division, genetics, ecology") }, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(16.dp))
                Text("Exam date", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Tomorrow", 3 to "In 3 days", 7 to "In a week", 14 to "In 2 weeks").forEach { (d, label) ->
                        FilterChip(selected = examDay == today() + d, onClick = { examDay = today() + d }, label = { Text(label) })
                    }
                    AssistChip(onClick = { picker = true }, label = { Text(dayLabel(examDay)) }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp)) })
                }
                if (error) {
                    Spacer(Modifier.height(12.dp))
                    Text("Something didn't load. Check your connection and retry.", color = c.coral)
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton(if (error) "Retry" else "Build my plan", { create() }, Modifier.fillMaxWidth(), enabled = subject.isNotBlank(), loading = busy)
                if (s.exams.isNotEmpty()) TextButton(onClick = { creating = false }, Modifier.fillMaxWidth()) { Text("Cancel", color = c.inkSoft) }
            }
        } else {
            LazyColumn(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.exams.sortedBy { it.examDay }, key = { it.id }) { e ->
                    val left = e.examDay - today()
                    val tasks = e.days.flatMap { it.tasks }
                    LACard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.exam(e.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.subject, style = MaterialTheme.typography.titleLarge, color = c.ink)
                                Text(dayLabel(e.examDay), style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                            }
                            Pill(when { left < 0 -> "Done"; left == 0L -> "Today"; else -> "$left days" }, bg = if (left in 0..3) c.coralSoft else c.surfaceAlt, fg = if (left in 0..3) c.coral else c.ink)
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size }, Modifier.fillMaxWidth().height(6.dp), color = c.success, trackColor = c.border, drawStopIndicator = {})
                    }
                }
            }
        }
    }

    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = examDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { examDay = (it / 86_400_000L).coerceAtLeast(today() + 1) }; picker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamDetail(nav: NavController, exam: Exam) {
    val c = LA.colors
    val scope = rememberCoroutineScope()
    val left = exam.examDay - today()
    val todayIndex = (today() - exam.planStartDay).toInt()
    var rebuilding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val tasks = exam.days.flatMap { it.tasks }

    fun toggle(dayIdx: Int, taskId: String) {
        var nowDone = false
        AppStore.update { s ->
            s.copy(exams = s.exams.map { e ->
                if (e.id != exam.id) e else e.copy(days = e.days.map { d ->
                    if (d.dayIndex != dayIdx) d else d.copy(tasks = d.tasks.map { t -> if (t.id == taskId) t.copy(done = !t.done).also { nowDone = it.done } else t })
                })
            })
        }
        if (nowDone) AppStore.recordActivity(5)
    }

    fun rebuild() {
        rebuilding = true; error = false
        scope.launch {
            runCatching { Ai.examPlan(exam.subject, exam.topics, left.toInt().coerceAtLeast(1), AppStore.learnerContext() + if (exam.mockScores.isNotEmpty()) " Mock scores so far: ${exam.mockScores.joinToString()}%." else "") }
                .onSuccess { p -> AppStore.update { s -> s.copy(exams = s.exams.map { if (it.id == exam.id) it.copy(days = planFrom(p), keyConcepts = p.keyConcepts.ifEmpty { it.keyConcepts }, planStartDay = today()) else it }) } }
                .onFailure { AppStore.logError("examRebuild", it); error = true }
            rebuilding = false
        }
    }

    fun runTask(t: ExamTask) {
        when (t.kind) {
            "mock" -> nav.navigate(Routes.quiz("mock", examId = exam.id))
            "flashcards" -> nav.navigate(Routes.flashcards("${exam.subject}: ${t.text}"))
            "practice" -> nav.navigate(Routes.quiz("quiz", "${exam.subject}: ${t.text}"))
            else -> nav.navigate(Routes.session("${exam.subject}: ${t.text}", 20))
        }
    }

    DetailScaffold(
        title = exam.subject, onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Exam options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Rebuild plan") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; rebuild() })
                DropdownMenuItem(text = { Text("Delete exam", color = c.coral) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.coral) }, onClick = {
                    menu = false; nav.popBackStack(); AppStore.update { s -> s.copy(exams = s.exams.filter { it.id != exam.id }) }
                })
            }
        },
        bottomBar = {
            Box(Modifier.background(c.canvas).navigationBarsPadding().padding(20.dp, 12.dp)) {
                PrimaryButton("Take a mock test", { nav.navigate(Routes.quiz("mock", examId = exam.id)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Timer)
            }
        },
    ) { inner ->
        LazyColumn(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when { left < 0 -> "Exam finished"; left == 0L -> "Exam is today"; left == 1L -> "1 day to go"; else -> "$left days to go" }, style = MaterialTheme.typography.headlineMedium, color = c.onHero)
                            Text(dayLabel(exam.examDay), color = c.onHero.copy(alpha = 0.7f))
                        }
                        ProgressRing(if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size, size = 64.dp, color = c.coral, track = c.onHero.copy(alpha = 0.15f), label = "${tasks.count { it.done }}/${tasks.size}", labelColor = c.onHero)
                    }
                    if (exam.mockScores.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Mock scores: " + exam.mockScores.joinToString("  ") { "$it%" }, color = c.onHero.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (rebuilding) item { GeneratingState(listOf("Rebuilding your plan…", "Weighting weak areas…")) }
            if (error) item { ErrorState(message = "Couldn't rebuild the plan. Your old plan is kept.", onRetry = { rebuild() }) }
            if (exam.keyConcepts.isNotEmpty()) item {
                Text("Key concepts", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exam.keyConcepts.forEach { k -> AssistChip(onClick = { nav.navigate(Routes.quiz("quiz", "${exam.subject}: $k")) }, label = { Text(k) }) }
                }
            }
            if (!rebuilding) items(exam.days, key = { it.dayIndex }) { d ->
                val isToday = d.dayIndex == todayIndex
                LACard(Modifier.fillMaxWidth(), color = if (isToday) c.coralSoft else c.surface, borderColor = if (isToday) c.coral.copy(alpha = 0.3f) else c.border) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.label, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
                        if (isToday) Pill("Today", bg = c.coral, fg = androidx.compose.ui.graphics.Color.White)
                    }
                    if (d.focus.isNotBlank()) Text(d.focus, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                    Spacer(Modifier.height(6.dp))
                    d.tasks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(t.done, { toggle(d.dayIndex, t.id) }, colors = CheckboxDefaults.colors(checkedColor = c.success))
                            Text(t.text, style = MaterialTheme.typography.bodyMedium, color = if (t.done) c.inkFaint else c.ink, modifier = Modifier.weight(1f))
                            IconButton(onClick = { runTask(t) }) { Icon(Icons.Rounded.PlayCircleOutline, "Start task", tint = c.cobalt) }
                        }
                    }
                }
            }
        }
    }
}
