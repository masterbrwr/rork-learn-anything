package com.rork.learnanything.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.PatternReport
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.launch

@Composable
fun MistakesScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf("All") }
    var showResolved by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf<PatternReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var making by remember { mutableStateOf(false) }
    val open = s.mistakes.filter { !it.resolved }
    val subjects = listOf("All") + s.mistakes.map { it.subject }.distinct()
    val list = s.mistakes.filter { (showResolved || !it.resolved) && (filter == "All" || it.subject == filter) }.reversed()

    fun analyze() {
        loading = true; error = false
        scope.launch {
            runCatching { Ai.mistakePatterns(open) }.onSuccess { report = it }.onFailure { AppStore.logError("patterns", it); error = true }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (open.size >= 2) analyze() }

    DetailScaffold(title = "Mistake bank", onBack = { nav.popBackStack() }) { inner ->
        LazyColumn(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(open.size.toString(), "to fix", Modifier.weight(1f), accent = c.coral)
                    StatTile(s.mistakes.count { it.resolved }.toString(), "fixed", Modifier.weight(1f), accent = c.success)
                    StatTile(open.map { it.concept }.distinct().size.toString(), "concepts", Modifier.weight(1f))
                }
            }
            if (open.size >= 2) item {
                LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SparkleMark(Modifier.size(20.dp), c.coral, animated = loading); Spacer(Modifier.width(8.dp))
                        Text("Pattern report", style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        loading -> { SkeletonBlock(Modifier.fillMaxWidth().height(16.dp)); Spacer(Modifier.height(6.dp)); SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp)) }
                        error -> {
                            Text("Something didn't load.", color = c.onHero)
                            TextButton(onClick = { analyze() }) { Text("Retry", color = c.coral) }
                        }
                        report != null -> {
                            val r = report!!
                            Text(r.insight, style = MaterialTheme.typography.bodyLarge, color = c.onHero)
                            if (r.rootCause.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(r.rootCause, style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.72f)) }
                            r.tips.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp)) }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryButton("Practice", { nav.navigate(Routes.quiz("mistakes", "")) }, Modifier.weight(1f))
                                if (r.prerequisite.isNotBlank()) SecondaryButton("Learn ${r.prerequisite}", { nav.navigate(Routes.session(r.prerequisite, 10)) }, Modifier.weight(1f), color = c.onHero)
                            }
                        }
                    }
                }
            }
            if (open.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Practice all", { nav.navigate(Routes.quiz("mistakes", "")) }, Modifier.weight(1f), icon = Icons.Rounded.Replay)
                    SecondaryButton(if (making) "Making…" else "Make flashcards", {
                        if (!making) scope.launch {
                            making = true
                            runCatching { Ai.flashcards(open.takeLast(15).joinToString("\n") { "Q: ${it.question} A: ${it.correctAnswer}. ${it.explanation}" }, 10) }
                                .onSuccess { p -> nav.navigate(Routes.deck(AppStore.addDeck(p.deckName.ifBlank { "My mistakes" }, "Review", p.cards.map { it.front to it.back }))) }
                                .onFailure { AppStore.logError("mistakeCards", it) }
                            making = false
                        }
                    }, Modifier.weight(1f), icon = Icons.Rounded.Style)
                }
            }
            if (s.mistakes.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subjects) { sub -> FilterChip(selected = filter == sub, onClick = { filter = sub }, label = { Text(sub) }) }
                    item { FilterChip(selected = showResolved, onClick = { showResolved = !showResolved }, label = { Text("Show fixed") }) }
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Rounded.CheckCircle, "Nothing to fix", "Wrong answers from lessons and quizzes land here, with why they happened and how to fix them.") }
            items(list, key = { it.id }) { m ->
                LACard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(m.concept, bg = if (m.resolved) c.successSoft else c.coralSoft, fg = if (m.resolved) c.success else c.coral)
                        Spacer(Modifier.weight(1f))
                        Text(m.subject, style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(m.question, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Row { Icon(Icons.Rounded.Close, null, tint = c.coral, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(m.yourAnswer.ifBlank { "No answer" }, color = c.inkSoft, style = MaterialTheme.typography.bodyMedium) }
                    Row { Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(m.correctAnswer, color = c.ink, style = MaterialTheme.typography.bodyMedium) }
                    if (m.explanation.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(m.explanation, color = c.inkSoft, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            AppStore.pendingTutorPrompt.value = "I got this wrong: \"${m.question}\". I answered \"${m.yourAnswer}\" but the answer is \"${m.correctAnswer}\". Help me understand why."
                            nav.navigate(Routes.TUTOR) { launchSingleTop = true }
                        }) { Text("Ask tutor", color = c.cobalt) }
                        TextButton(onClick = { AppStore.update { st -> st.copy(mistakes = st.mistakes.map { if (it.id == m.id) it.copy(resolved = !it.resolved) else it }) } }) {
                            Text(if (m.resolved) "Reopen" else "Mark fixed", color = c.success)
                        }
                    }
                }
            }
        }
    }
}
