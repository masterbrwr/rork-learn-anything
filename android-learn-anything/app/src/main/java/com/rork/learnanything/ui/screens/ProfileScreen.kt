package com.rork.learnanything.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.*
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import com.rork.learnanything.ui.theme.worldStyle
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.rounded.Logout

private fun relativeTime(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 1440 -> "${m / 60} h ago"
        else -> "${m / 1440} d ago"
    }
}

fun achievementIcon(key: String): ImageVector = when (key) {
    "route" -> Icons.Rounded.Route
    "lesson" -> Icons.AutoMirrored.Rounded.MenuBook
    "quiz" -> Icons.Rounded.Quiz
    "flame" -> Icons.Rounded.LocalFireDepartment
    "clock" -> Icons.Rounded.Schedule
    "trophy" -> Icons.Rounded.EmojiEvents
    "globe" -> Icons.Rounded.Public
    "star" -> Icons.Rounded.Star
    "bolt" -> Icons.Rounded.Bolt
    "cards" -> Icons.Rounded.Style
    "note" -> Icons.AutoMirrored.Rounded.Notes
    "timer" -> Icons.Rounded.Timer
    else -> Icons.Rounded.WorkspacePremium
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    var editName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    val session by Auth.session.collectAsStateWithLifecycle()
    val syncStatus by Auth.syncStatus.collectAsStateWithLifecycle()
    val lastSynced by Auth.lastSyncedAt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val level = levelFor(s.xp)
    val totalMinutes = s.activity.sumOf { it.minutes }

    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero, shape = RoundedCornerShape(24.dp), padding = PaddingValues(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    ProgressRing(levelProgress(s.xp), size = 80.dp, stroke = 4.dp, color = c.amber, track = c.onHero.copy(alpha = 0.15f))
                    Avatar(s.profile.name, size = 62.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Overline(if (s.isPremium) "Premium member" else "Learner", color = if (s.isPremium) c.amber else c.onHero.copy(alpha = 0.6f))
                    Spacer(Modifier.height(2.dp))
                    Text(s.profile.name.ifBlank { "Learner" }, style = MaterialTheme.typography.headlineMedium, color = c.onHero, maxLines = 1)
                    Text("Level $level · ${s.xp} XP", style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.75f))
                }
                IconButton(onClick = { nameDraft = s.profile.name; editName = true }) { Icon(Icons.Rounded.Edit, "Edit name", tint = c.onHero.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            val toNext = 120 * level * (level + 1) / 2 - s.xp
            LinearProgressIndicator(
                progress = { levelProgress(s.xp) }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = c.amber, trackColor = c.onHero.copy(alpha = 0.12f), drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Level $level", style = MaterialTheme.typography.labelMedium, color = c.onHero.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text("$toNext XP to level ${level + 1}", style = MaterialTheme.typography.labelMedium, color = c.onHero.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${s.streak}", "day streak", Modifier.weight(1f), accent = c.coral)
            StatTile(minutesLabel(totalMinutes), "learned", Modifier.weight(1f))
            StatTile("${s.lessonsCompleted}", "lessons", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${s.bestStreak}", "best streak", Modifier.weight(1f))
            StatTile("${s.quizzesTaken}", "quizzes", Modifier.weight(1f))
            StatTile("${s.cardReviews}", "card reviews", Modifier.weight(1f))
        }

        // Activity heatmap
        Spacer(Modifier.height(22.dp))
        SectionHeader("Last 4 weeks")
        Spacer(Modifier.height(8.dp))
        LACard(Modifier.fillMaxWidth()) {
            val d = today()
            val goal = s.profile.dailyGoalMinutes.coerceAtLeast(1)
            (0 until 4).forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { i ->
                        val day = d - 27 + w * 7 + i
                        val a = s.activity.firstOrNull { it.day == day }
                        val f = ((a?.minutes ?: 0).toFloat() / goal).coerceIn(0f, 1f)
                        val active = a != null && (a.minutes > 0 || a.xp > 0)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (!active) c.surfaceAlt else c.coral.copy(alpha = 0.25f + 0.75f * f))
                                .semantics { contentDescription = "${a?.minutes ?: 0} minutes" },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Daily goal: ${s.profile.dailyGoalMinutes} min", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        }

        // Knowledge map
        Spacer(Modifier.height(22.dp))
        SectionHeader("Knowledge map")
        Spacer(Modifier.height(8.dp))
        LACard(Modifier.fillMaxWidth()) {
            val worlds = s.courses.groupBy { it.world }
            if (worlds.isEmpty() && s.topicStats.isEmpty()) Text("Your strengths and weak spots appear here as you learn.", color = c.inkSoft)
            worlds.forEach { (w, courses) ->
                val ws = worldStyle(w)
                val p = courses.map { it.progress }.average().toFloat()
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ws.icon, null, tint = ws.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(ws.name, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.width(110.dp))
                    LinearProgressIndicator(progress = { p }, Modifier.weight(1f).height(8.dp).clip(CircleShape), color = ws.accent, trackColor = ws.soft, drawStopIndicator = {})
                    Spacer(Modifier.width(10.dp))
                    Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.inkSoft)
                }
            }
            val strong = s.topicStats.filter { it.correct >= 2 && it.wrong <= it.correct / 3 }.sortedByDescending { it.correct }.take(5)
            val weak = AppStore.weakTopics(s, 5)
            if (strong.isNotEmpty()) {
                Spacer(Modifier.height(10.dp)); Text("Strong", style = MaterialTheme.typography.labelLarge, color = c.success)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { strong.forEach { Pill(it.topic, bg = c.successSoft, fg = c.success, modifier = Modifier.padding(top = 6.dp)) } }
            }
            if (weak.isNotEmpty()) {
                Spacer(Modifier.height(10.dp)); Text("Needs work", style = MaterialTheme.typography.labelLarge, color = c.coral)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weak.forEach { t -> Pill(t.topic, bg = c.coralSoft, fg = c.coral, modifier = Modifier.padding(top = 6.dp).pressable({ nav.navigate(Routes.quiz("mistakes", t.topic)) })) }
                }
            }
        }

        // Achievements
        Spacer(Modifier.height(22.dp))
        SectionHeader("Achievements · ${s.unlocked.size}/${ACHIEVEMENTS.size}")
        Spacer(Modifier.height(8.dp))
        ACHIEVEMENTS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    val got = a.id in s.unlocked
                    LACard(Modifier.weight(1f).height(128.dp), color = if (got) c.amberSoft else c.surface, borderColor = if (got) c.amber.copy(alpha = 0.35f) else c.border, padding = PaddingValues(10.dp)) {
                        Column(Modifier.fillMaxSize().alpha(if (got) 1f else 0.45f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(if (got) achievementIcon(a.icon) else Icons.Rounded.Lock, null, tint = if (got) c.amber else c.inkFaint, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(a.title, style = MaterialTheme.typography.labelLarge, color = c.ink, textAlign = TextAlign.Center, maxLines = 2)
                            Text(a.detail, style = MaterialTheme.typography.labelSmall, color = c.inkSoft, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Premium
        Spacer(Modifier.height(12.dp))
        LACard(Modifier.fillMaxWidth(), color = c.hero, borderColor = c.hero, onClick = { nav.navigate(Routes.PRICING) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = c.amber)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.isPremium) "Premium active" else "Learn Anything Premium", style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text("Unlimited tutor, paths and exam prep", style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.7f))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onHero)
            }
        }

        // Account
        if (Auth.available) {
            Spacer(Modifier.height(22.dp))
            SectionHeader("Account")
            Spacer(Modifier.height(8.dp))
            val u = session?.user
            if (u == null) {
                LACard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.auth()) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CloudUpload, null, tint = c.ink)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Back up your progress", style = MaterialTheme.typography.titleMedium, color = c.ink)
                            Text("Create a free account with your email to keep everything safe across devices.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
                    }
                }
            } else {
                LACard(Modifier.fillMaxWidth(), padding = PaddingValues(4.dp)) {
                    val method = when (u.provider) { "google" -> "Google"; "phone" -> "Phone"; else -> "Email" }
                    ListItem(
                        headlineContent = { Text(u.name ?: u.email ?: u.phone ?: "Signed in", color = c.ink) },
                        supportingContent = { Text(listOfNotNull(u.email ?: u.phone, "via $method").joinToString(" · "), color = c.inkSoft) },
                        leadingContent = { Avatar(u.name ?: s.profile.name, size = 40.dp) },
                        colors = ListItemDefaults.colors(containerColor = c.surface),
                    )
                    HorizontalDivider(color = c.border)
                    val syncLine = when (syncStatus) {
                        "syncing" -> "Backing up…"
                        "error" -> "Last backup failed. Tap to retry."
                        else -> if (lastSynced > 0) "Backed up ${relativeTime(lastSynced)}" else "Not backed up yet"
                    }
                    ListItem(
                        headlineContent = { Text("Cloud backup", color = c.ink) },
                        supportingContent = { Text(syncLine, color = if (syncStatus == "error") c.coral else c.inkSoft) },
                        leadingContent = { Icon(if (syncStatus == "error") Icons.Rounded.CloudOff else Icons.Rounded.CloudDone, null, tint = if (syncStatus == "error") c.coral else c.success) },
                        trailingContent = {
                            if (syncStatus == "syncing") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.ink)
                            else TextButton(onClick = { scope.launch { Auth.pushNow() } }) { Text("Back up now", color = c.cobalt) }
                        },
                        colors = ListItemDefaults.colors(containerColor = c.surface),
                    )
                    HorizontalDivider(color = c.border)
                    ListItem(
                        headlineContent = { Text("Sign out", color = c.ink) },
                        supportingContent = { Text("Your progress stays on this phone.", color = c.inkSoft) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = c.ink) },
                        modifier = Modifier.pressable({ if (!accountBusy) scope.launch { accountBusy = true; runCatching { Auth.signOut() }; accountBusy = false } }),
                        colors = ListItemDefaults.colors(containerColor = c.surface),
                    )
                    HorizontalDivider(color = c.border)
                    ListItem(
                        headlineContent = { Text("Delete account", color = c.coral) },
                        leadingContent = { Icon(Icons.Rounded.PersonRemove, null, tint = c.coral) },
                        modifier = Modifier.pressable({ confirmDelete = true }),
                        colors = ListItemDefaults.colors(containerColor = c.surface),
                    )
                }
            }
            accountError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.coral)
            }
        }

        // Settings
        Spacer(Modifier.height(22.dp))
        SectionHeader("Listening")
        Spacer(Modifier.height(8.dp))
        var voicePicker by remember { mutableStateOf(false) }
        if (voicePicker) VoicePickerDialog { voicePicker = false }
        LACard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Headphones, c.hero, c.coral, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Listen instead of read", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Lessons, sessions and quizzes are read aloud with live highlighting.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
                Switch(s.listenMode, { v -> AppStore.update { it.copy(listenMode = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = c.coral))
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.border)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hands-free", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Move to the next step on its own after it's read.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
                Switch(s.listenAutoAdvance, { v -> AppStore.update { it.copy(listenAutoAdvance = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = c.coral))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Read tutor replies aloud", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Hear the tutor's answers as they arrive.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
                Switch(s.tutorVoice, { v -> AppStore.update { it.copy(tutorVoice = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = c.coral))
            }
            Spacer(Modifier.height(10.dp))
            ListItem(
                headlineContent = { Text("Voice and speed", color = c.ink) },
                supportingContent = { Text("Playing at ${speedLabel(s.narratorRate)} · tap to choose a voice", color = c.inkSoft) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { voicePicker = true },
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader("Settings")
        Spacer(Modifier.height(8.dp))
        LACard(Modifier.fillMaxWidth()) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(8.dp))
            val modes = listOf("light" to "Light", "dark" to "Dark", "system" to "System")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = s.themeMode == k, onClick = { AppStore.update { it.copy(themeMode = k) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.coralSoft, activeContentColor = c.coral),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Daily goal", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 45, 60).forEach { m ->
                    FilterChip(selected = s.profile.dailyGoalMinutes == m, onClick = { AppStore.update { it.copy(profile = it.profile.copy(dailyGoalMinutes = m)) } }, label = { Text("$m min") })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Learning style", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Visual", "Examples", "Step by step", "Practice first").forEach { st ->
                    FilterChip(selected = s.profile.learningStyle == st, onClick = { AppStore.update { it.copy(profile = it.profile.copy(learningStyle = st)) } }, label = { Text(st) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LACard(Modifier.fillMaxWidth(), padding = PaddingValues(4.dp)) {
            ListItem(
                headlineContent = { Text("Privacy & data") },
                supportingContent = { Text(if (session != null) "Your progress is backed up privately to your account. AI requests send only the text needed to answer." else "Your learning data stays on this device. AI requests send only the text needed to answer.") },
                leadingContent = { Icon(Icons.Rounded.Shield, null, tint = c.success) },
                colors = ListItemDefaults.colors(containerColor = c.surface),
            )
            HorizontalDivider(color = c.border)
            ListItem(
                headlineContent = { Text("Reset all progress", color = c.coral) },
                leadingContent = { Icon(Icons.Rounded.RestartAlt, null, tint = c.coral) },
                modifier = Modifier.pressable({ confirmReset = true }),
                colors = ListItemDefaults.colors(containerColor = c.surface),
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (editName) AlertDialog(
        onDismissRequest = { editName = false },
        title = { Text("Your name") },
        text = { OutlinedTextField(nameDraft, { nameDraft = it.take(30) }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
        confirmButton = { TextButton(onClick = { AppStore.update { it.copy(profile = it.profile.copy(name = nameDraft.trim())) }; editName = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!accountBusy) confirmDelete = false },
        title = { Text("Delete your account?") },
        text = { Text("Your account and cloud backup are permanently deleted. Progress on this phone stays until you reset it. Active subscriptions must be cancelled in Google Play.") },
        confirmButton = {
            TextButton(enabled = !accountBusy, onClick = {
                scope.launch {
                    accountBusy = true; accountError = null
                    runCatching { Auth.deleteAccount() }.onFailure { accountError = it.message ?: "Couldn't delete the account. Try again." }
                    accountBusy = false; confirmDelete = false
                }
            }) { if (accountBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.coral) else Text("Delete", color = c.coral) }
        },
        dismissButton = { TextButton(enabled = !accountBusy, onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset everything?") },
        text = { Text("Paths, XP, streaks, notes, flashcards and chat history will be erased from this device. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; AppStore.update { Seed.initialState() }; nav.goHome() }) { Text("Reset", color = c.coral) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}
