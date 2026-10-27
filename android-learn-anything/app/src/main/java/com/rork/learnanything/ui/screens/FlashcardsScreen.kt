package com.rork.learnanything.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.Flashcard
import com.rork.learnanything.data.Worlds
import com.rork.learnanything.data.today
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.worldStyle
import kotlinx.coroutines.launch

@Composable
fun FlashcardsScreen(nav: NavController, generate: String) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val scope = rememberCoroutineScope()
    var showCreate by rememberSaveable { mutableStateOf(generate.isNotBlank()) }
    var source by rememberSaveable { mutableStateOf(generate) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val due = s.cards.count { it.dueDay <= today() }

    fun create() {
        busy = true; error = null
        scope.launch {
            runCatching { Ai.flashcards(source, 10) }
                .onSuccess { p ->
                    if (p.cards.isEmpty()) error = "No cards came back. Try a more specific topic."
                    else {
                        val id = AppStore.addDeck(p.deckName.ifBlank { source.take(30) }, Worlds.displayName[Worlds.classify(source)] ?: "General", p.cards.map { it.front to it.back })
                        AppStore.track("deck_generated")
                        showCreate = false; source = ""
                        nav.navigate(Routes.deck(id))
                    }
                }
                .onFailure { AppStore.logError("flashcards", it); error = "Something didn't load. Check your connection and retry." }
            busy = false
        }
    }

    DetailScaffold(title = "Flashcards", onBack = { nav.popBackStack() }, actions = {
        IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, "New deck") }
    }) { inner ->
        LazyColumn(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero) {
                    Text(if (due > 0) "$due cards due" else "You're all caught up", style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                    Text("Spaced repetition shows each card right before you'd forget it.", color = c.onHero.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
                    if (due > 0) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Review all due", { nav.navigate(Routes.review()) }, icon = Icons.Rounded.PlayArrow)
                    }
                }
            }
            if (s.decks.isEmpty()) item { EmptyState(Icons.Rounded.Style, "No decks yet", "Generate a deck from any topic, or from your notes.", action = "Create a deck", onAction = { showCreate = true }) }
            items(s.decks, key = { it.id }) { d ->
                val cards = s.cards.filter { it.deckId == d.id }
                val dueD = cards.count { it.dueDay <= today() }
                val mastered = cards.count { it.intervalDays >= 21 }
                val ws = worldStyle(d.world)
                LACard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.deck(d.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(ws.icon, ws.soft, ws.accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleLarge, color = c.ink)
                            Text("${cards.size} cards · $mastered mastered", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                        }
                        if (dueD > 0) Pill("$dueD due", bg = c.coralSoft, fg = c.coral)
                    }
                }
            }
        }
    }

    if (showCreate) AlertDialog(
        onDismissRequest = { if (!busy) showCreate = false },
        title = { Text("Generate a deck") },
        text = {
            Column {
                Text("Type a topic or paste notes. I'll write focused cards, one idea each.", color = c.inkSoft)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp), placeholder = { Text("e.g. The Krebs cycle") }, shape = RoundedCornerShape(14.dp), enabled = !busy)
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = c.coral, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { create() }, enabled = source.isNotBlank() && !busy) { Text(if (busy) "Generating…" else if (error != null) "Retry" else "Generate") } },
        dismissButton = { TextButton(onClick = { showCreate = false }, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
fun DeckScreen(nav: NavController, deckId: String) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val deck = s.decks.firstOrNull { it.id == deckId }
    val cards = s.cards.filter { it.deckId == deckId }
    var adding by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    DetailScaffold(
        title = deck?.name ?: "Deck", onBack = { nav.popBackStack() },
        actions = {
            if (deck != null) {
                IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Add card") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Deck options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Quiz me on this deck") }, leadingIcon = { Icon(Icons.Rounded.Quiz, null) }, onClick = { menu = false; nav.navigate(Routes.quiz("quiz", deck.name)) })
                    DropdownMenuItem(text = { Text("Delete deck", color = c.coral) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.coral) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        },
        bottomBar = {
            if (cards.isNotEmpty()) Box(Modifier.background(c.canvas).navigationBarsPadding().padding(20.dp, 12.dp)) {
                val due = cards.count { it.dueDay <= today() }
                PrimaryButton(if (due > 0) "Review $due due" else "Practice all ${cards.size}", { nav.navigate(Routes.review(deckId)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            }
        },
    ) { inner ->
        if (deck == null) {
            Box(Modifier.padding(inner)) { ErrorState(message = "This deck was deleted.", onBack = { nav.popBackStack() }, onHome = { nav.goHome() }) }
            return@DetailScaffold
        }
        LazyColumn(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cards.isEmpty()) item { EmptyState(Icons.Rounded.Style, "Empty deck", "Add your first card.", action = "Add card", onAction = { adding = true }) }
            items(cards, key = { it.id }) { card ->
                LACard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(card.front, style = MaterialTheme.typography.titleMedium, color = c.ink)
                            Spacer(Modifier.height(2.dp))
                            Text(card.back, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                            Spacer(Modifier.height(6.dp))
                            Text(intervalLabel(card), style = MaterialTheme.typography.labelSmall, color = if (card.dueDay <= today()) c.coral else c.success)
                        }
                        IconButton(onClick = { AppStore.deleteCard(card.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete card", tint = c.inkFaint) }
                    }
                }
            }
        }
    }

    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text("New card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(front, { front = it }, Modifier.fillMaxWidth(), label = { Text("Front") }, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(back, { back = it }, Modifier.fillMaxWidth(), label = { Text("Back") }, shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = { TextButton(onClick = { AppStore.addCards(deckId, listOf(front.trim() to back.trim())); front = ""; back = ""; adding = false }, enabled = front.isNotBlank() && back.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this deck?") }, text = { Text("All ${cards.size} cards will be removed.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; nav.popBackStack(); AppStore.deleteDeck(deckId) }) { Text("Delete", color = c.coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

private fun intervalLabel(card: Flashcard): String {
    val d = card.dueDay - today()
    return when {
        d <= 0 -> "Due now"
        d == 1L -> "Next review tomorrow"
        else -> "Next review in $d days"
    }
}

@Composable
fun ReviewScreen(nav: NavController, deckId: String) {
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    val queue = remember {
        val all = AppStore.state.value.cards.filter { deckId == "all" || it.deckId == deckId }
        val due = all.filter { it.dueDay <= today() }
        (due.ifEmpty { all }).shuffled().map { it.id }.toMutableStateList()
    }
    var total by remember { mutableIntStateOf(queue.size) }
    var reviewed by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val s by AppStore.state.collectAsStateWithLifecycle()
    val card = queue.firstOrNull()?.let { id -> s.cards.firstOrNull { it.id == id } }
    val rot = remember(card?.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(flipped, card?.id) { rot.animateTo(if (flipped) 180f else 0f, tween(420)) }
    val rotation = rot.value

    DetailScaffold(title = "Review", onBack = { nav.popBackStack() }, actions = {
        if (card != null) Text("$reviewed / $total", color = c.inkSoft, modifier = Modifier.padding(end = 16.dp))
    }) { inner ->
        Column(Modifier.padding(inner).consumeWindowInsets(inner).navigationBarsPadding().fillMaxSize().padding(20.dp)) {
            if (card == null) {
                Spacer(Modifier.weight(1f))
                EmptyState(Icons.Rounded.CheckCircle, if (reviewed > 0) "Session done" else "Nothing to review", if (reviewed > 0) "$reviewed cards reviewed. Your next reviews are scheduled automatically." else "Add cards to a deck to start reviewing.", action = "Done", onAction = { nav.popBackStack() })
                Spacer(Modifier.weight(1f))
                return@Column
            }
            LinearProgressIndicator(progress = { if (total == 0) 0f else reviewed.toFloat() / total }, Modifier.fillMaxWidth().height(6.dp), color = c.coral, trackColor = c.border, drawStopIndicator = {})
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }
                    .pressable({ flipped = !flipped }, label = if (flipped) "Show question" else "Show answer"),
            ) {
                val showBack = rotation > 90f
                LACard(Modifier.fillMaxSize().graphicsLayer { if (showBack) rotationY = 180f }, color = if (showBack) c.cobaltSoft else c.surface) {
                    Text(if (showBack) "ANSWER" else "QUESTION", style = MaterialTheme.typography.labelMedium, color = if (showBack) c.cobalt else c.inkSoft)
                    Spacer(Modifier.weight(1f))
                    Text(if (showBack) card.back else card.front, style = if (showBack) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    if (!showBack) Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(18.dp))
            if (!flipped) PrimaryButton("Show answer", { flipped = true }, Modifier.fillMaxWidth())
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Again", c.coral, "<1d"), Triple("Hard", c.amber, "1d"), Triple("Good", c.success, "3d+"), Triple("Easy", c.cobalt, "1w+")).forEachIndexed { i, (label, color, hint) ->
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            AppStore.rateCard(card.id, i)
                            queue.removeAt(0)
                            if (i == 0) { queue.add(card.id); total++ }
                            reviewed++
                            flipped = false
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp), shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, color),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                            Text(hint, color = c.inkSoft, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
