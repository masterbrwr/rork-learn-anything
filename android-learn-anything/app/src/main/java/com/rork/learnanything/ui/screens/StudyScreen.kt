package com.rork.learnanything.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.today
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA

@Composable
fun StudyScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val course = s.courses.firstOrNull { it.id == s.activeCourseId }
    var topic by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    val due = s.cards.count { it.dueDay <= today() }
    val openMistakes = s.mistakes.count { !it.resolved }
    val nextExam = s.exams.filter { it.examDay >= today() }.minByOrNull { it.examDay }
    val suggestion = course?.currentLesson?.title?.let { "${course.title}: $it" } ?: ""

    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Study", style = MaterialTheme.typography.headlineLarge, color = c.ink)
        Text("Focused sessions and the tools that make it stick.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
        Spacer(Modifier.height(18.dp))

        // Focus session
        LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = c.coral)
                Spacer(Modifier.width(8.dp))
                Text("Study mode", style = MaterialTheme.typography.titleLarge, color = c.onHero)
            }
            Text("Learn, practice, quiz, then review. One timed block, built from your weak spots.", color = c.onHero.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                topic, { topic = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(suggestion.ifBlank { "What do you want to study?" }, color = c.onHero.copy(alpha = 0.45f)) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { nav.navigate(Routes.session(topic.ifBlank { suggestion }, minutes)) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.onHero, unfocusedTextColor = c.onHero, cursorColor = c.coral,
                    focusedBorderColor = c.coral, unfocusedBorderColor = c.onHero.copy(alpha = 0.25f),
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 25, 45).forEach { m ->
                    val sel = minutes == m
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (sel) c.coral else Color.Transparent)
                            .border(1.dp, if (sel) c.coral else c.onHero.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .pressable({ minutes = m }, label = "$m minutes").padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("$m min", color = if (sel) Color.White else c.onHero, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                "Start session", { nav.navigate(Routes.session(topic.ifBlank { suggestion }, minutes)) }, Modifier.fillMaxWidth(),
                enabled = topic.isNotBlank() || suggestion.isNotBlank(), icon = Icons.Rounded.PlayArrow,
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader("Tools")
        Spacer(Modifier.height(8.dp))
        val tools = listOf(
            Tool("Flashcards", if (due > 0) "$due due today" else "All caught up", Icons.Rounded.Style, c.cobalt, c.cobaltSoft) { nav.navigate(Routes.FLASHCARDS) },
            Tool("Notes", "${s.notes.size} notes", Icons.AutoMirrored.Rounded.Notes, c.amber, c.amberSoft) { nav.navigate(Routes.NOTES) },
            Tool("Mistake bank", if (openMistakes > 0) "$openMistakes to fix" else "Nothing to fix", Icons.Rounded.ErrorOutline, c.coral, c.coralSoft) { nav.navigate(Routes.MISTAKES) },
            Tool("Exam prep", nextExam?.let { "${it.examDay - today()} days to ${it.subject}" } ?: "Plan an exam", Icons.Rounded.EventNote, c.success, c.successSoft) { nav.navigate(Routes.exam()) },
            Tool("Smart quiz", "Adapts as you answer", Icons.Rounded.Quiz, c.cobalt, c.cobaltSoft) { nav.navigate(Routes.quiz("quiz", "")) },
            Tool("Mock test", "Timed, exam-style", Icons.Rounded.Timer, c.ink, c.surfaceAlt) { nav.navigate(Routes.quiz("mock", course?.title ?: "")) },
        )
        tools.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t ->
                    LACard(Modifier.weight(1f).height(132.dp), onClick = t.onClick) {
                        IconBadge(t.icon, t.soft, t.color, 40.dp)
                        Spacer(Modifier.weight(1f))
                        Text(t.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkSoft, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private data class Tool(val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val soft: Color, val onClick: () -> Unit)
