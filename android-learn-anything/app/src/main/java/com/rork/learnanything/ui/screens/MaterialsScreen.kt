package com.rork.learnanything.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rork.learnanything.data.Ai
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Note
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.launch

@Composable
fun MaterialsScreen(nav: NavController) {
    val c = LA.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var makeCards by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { text ->
                source = text.take(50000)
                if (title.isBlank()) title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Course material"
                status = if (text.isBlank()) "This file has no readable text. Try a text-based .txt or .md file." else "Material imported. Review it before generating study tools."
            }.onFailure { status = "Couldn't read that file. Try pasting the text instead." }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("Course materials", style = MaterialTheme.typography.headlineLarge, color = c.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Turn your class content into a focused study kit.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = c.surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Icon(Icons.Rounded.UploadFile, null, tint = c.cobalt)
                    Spacer(Modifier.width(10.dp))
                    Text("Import course text", style = MaterialTheme.typography.titleMedium, color = c.ink)
                }
                Spacer(Modifier.height(8.dp))
                Text("Currently supports readable .txt and .md files, plus pasted text. PDF, Word, PowerPoint, Google Docs, handwriting and YouTube import are not enabled yet.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(8.dp)); Text("Choose a text file")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Course or topic name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = c.surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Paste lecture notes, a transcript, or reading excerpts", color = c.ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = source, onValueChange = { source = it.take(50000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                    cursorBrush = SolidColor(c.cobalt),
                    decorationBox = { inner ->
                        Box {
                            if (source.isEmpty()) Text("Paste your own class material here…", color = c.inkSoft)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text("${source.length}/50,000 characters", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = makeCards, onCheckedChange = { makeCards = it })
            Text("Also generate flashcards", color = c.ink)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (source.isBlank() || busy) return@Button
                busy = true; status = "Building your study kit…"
                scope.launch {
                    try {
                        val subject = title.ifBlank { "Course material" }
                        val notes = Ai.notesAction("create structured study notes with headings, key ideas, definitions, examples, and a short recap", source)
                        AppStore.saveNote(Note(id = com.rork.learnanything.data.newId(), title = subject, body = notes, subject = subject))
                        var cardMessage = ""
                        if (makeCards) {
                            val payload = Ai.flashcards(source, 12)
                            if (payload.cards.isNotEmpty()) {
                                AppStore.addDeck(payload.deckName.ifBlank { "$subject · Flashcards" }, subject, payload.cards.map { it.front to it.back })
                                cardMessage = " Flashcards saved."
                            }
                        }
                        status = "Study notes saved.$cardMessage Open Study to review your tools."
                    } catch (e: Exception) {
                        status = e.message ?: "Couldn't generate the study kit. Check your connection and sign-in."
                    } finally { busy = false }
                }
            },
            enabled = source.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Generate my study kit") }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
        }
        Spacer(Modifier.height(30.dp))
    }
}
