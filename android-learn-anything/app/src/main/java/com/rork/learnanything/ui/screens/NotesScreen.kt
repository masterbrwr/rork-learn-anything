package com.rork.learnanything.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.Note
import com.rork.learnanything.data.Worlds
import com.rork.learnanything.data.newId
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    var query by rememberSaveable { mutableStateOf("") }
    val notes = s.notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
        .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    val fmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    DetailScaffold(title = "Notes", onBack = { nav.popBackStack() }, bottomBar = {
        Box(Modifier.background(c.canvas).navigationBarsPadding().padding(20.dp, 12.dp)) {
            PrimaryButton("New note", { nav.navigate(Routes.note()) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }) { inner ->
        LazyColumn(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Search notes") },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                )
            }
            if (notes.isEmpty()) item {
                EmptyState(Icons.AutoMirrored.Rounded.Notes, if (query.isBlank()) "No notes yet" else "No matches", if (query.isBlank()) "Write as you learn. I can summarize, spot gaps and turn notes into flashcards or a quiz." else "Try a different word.")
            }
            items(notes, key = { it.id }) { n ->
                LACard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.note(n.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        if (n.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = c.coral, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(n.body.replace("==", "").replace("**", "").take(140), style = MaterialTheme.typography.bodyMedium, color = c.inkSoft, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Text("${n.subject} · ${fmt.format(Date(n.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                }
            }
        }
    }
}

@Composable
fun NoteEditorScreen(nav: NavController, noteId: String) {
    val c = LA.colors
    val scope = rememberCoroutineScope()
    val id = remember { if (noteId == "new") newId() else noteId }
    val existing = remember { AppStore.state.value.notes.firstOrNull { it.id == id } }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf<String?>(null) }
    var aiResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    // Autosave
    LaunchedEffect(title, body.text, pinned) {
        if (deleted || (title.isBlank() && body.text.isBlank())) return@LaunchedEffect
        delay(500)
        AppStore.saveNote(Note(id, title.trim(), body.text, Worlds.displayName[Worlds.classify(title + " " + body.text)] ?: "General", System.currentTimeMillis(), pinned))
    }

    fun highlight() {
        val sel = body.selection
        if (sel.collapsed) return
        val t = body.text
        val start = sel.min; val end = sel.max
        val inner = t.substring(start, end)
        val wrapped = if (inner.startsWith("==") && inner.endsWith("==") && inner.length >= 4) inner.drop(2).dropLast(2) else "==$inner=="
        body = TextFieldValue(t.substring(0, start) + wrapped + t.substring(end), TextRange(start + wrapped.length))
    }

    fun runAi(kind: String) {
        val text = "${title}\n${body.text}"
        if (text.isBlank()) return
        aiBusy = kind; aiError = null
        scope.launch {
            when (kind) {
                "flashcards" -> runCatching { Ai.flashcards(text, 8) }.onSuccess { p ->
                    val deck = AppStore.addDeck(p.deckName.ifBlank { title.ifBlank { "From notes" } }, "Notes", p.cards.map { it.front to it.back })
                    nav.navigate(Routes.deck(deck))
                }.onFailure { AppStore.logError("notesCards", it); aiError = kind }
                else -> runCatching { Ai.notesAction(kind, text) }.onSuccess { aiResult = kind to it }.onFailure { AppStore.logError("notesAi", it); aiError = kind }
            }
            aiBusy = null
        }
    }

    DetailScaffold(
        title = if (noteId == "new" && existing == null) "New note" else "Note",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { pinned = !pinned }) { Icon(if (pinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin, if (pinned) "Unpin" else "Pin", tint = if (pinned) c.coral else c.inkFaint) }
            IconButton(onClick = { preview = !preview }) { Icon(if (preview) Icons.Rounded.Edit else Icons.Rounded.Visibility, if (preview) "Edit" else "Preview") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete note") }
        },
        bottomBar = {
            Column(Modifier.background(c.canvas).navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = c.border)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!preview) AssistChip(onClick = { highlight() }, label = { Text("Highlight") }, leadingIcon = { Icon(Icons.Rounded.BorderColor, null, Modifier.size(16.dp)) })
                    listOf(
                        Triple("summarize", "Summarize", Icons.Rounded.Summarize),
                        Triple("gaps", "Find gaps", Icons.Rounded.ManageSearch),
                        Triple("flashcards", "Flashcards", Icons.Rounded.Style),
                    ).forEach { (k, label, icon) ->
                        AssistChip(
                            onClick = { runAi(k) }, enabled = aiBusy == null && body.text.isNotBlank(),
                            label = { Text(if (aiBusy == k) "Working…" else label) },
                            leadingIcon = { if (aiBusy == k) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(icon, null, Modifier.size(16.dp), tint = c.cobalt) },
                        )
                    }
                    AssistChip(onClick = { nav.navigate(Routes.quiz("quiz", title.ifBlank { body.text.take(80) })) }, enabled = body.text.isNotBlank(), label = { Text("Quiz me") }, leadingIcon = { Icon(Icons.Rounded.Quiz, null, Modifier.size(16.dp), tint = c.cobalt) })
                }
            }
        },
    ) { inner ->
        Column(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            BasicTextField(
                title, { title = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = c.ink), cursorBrush = SolidColor(c.coral), singleLine = true,
                decorationBox = { inner -> Box { if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineMedium, color = c.inkFaint); inner() } },
            )
            aiError?.let { k ->
                LACard(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = c.coralSoft, borderColor = c.coral.copy(alpha = 0.25f)) {
                    Text("Something didn't load.", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Row { TextButton(onClick = { runAi(k) }) { Text("Retry", color = c.coral) }; TextButton(onClick = { aiError = null }) { Text("Dismiss", color = c.inkSoft) } }
                }
            }
            aiResult?.let { (k, text) ->
                LACard(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = c.cobaltSoft, borderColor = c.cobalt.copy(alpha = 0.2f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SparkleMark(Modifier.size(18.dp), c.cobalt); Spacer(Modifier.width(8.dp))
                        Text(if (k == "gaps") "What might be missing" else "Summary", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                        IconButton(onClick = { aiResult = null }) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.inkSoft) }
                    }
                    RichText(text, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { body = TextFieldValue(body.text.trimEnd() + "\n\n" + text); aiResult = null }) { Text("Add to note", color = c.cobalt) }
                }
            }
            if (preview) {
                RichText(body.text.ifBlank { "Nothing written yet." }, Modifier.padding(vertical = 8.dp))
            } else {
                BasicTextField(
                    body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(vertical = 8.dp),
                    textStyle = TextStyle(color = c.ink, fontSize = 17.sp, lineHeight = 26.sp), cursorBrush = SolidColor(c.coral),
                    decorationBox = { inner -> Box { if (body.text.isEmpty()) Text("Start writing. Select text and tap Highlight to mark key ideas.", color = c.inkFaint, fontSize = 17.sp); inner() } },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete note?") },
        confirmButton = { TextButton(onClick = { deleted = true; AppStore.deleteNote(id); confirmDelete = false; nav.popBackStack() }) { Text("Delete", color = c.coral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
