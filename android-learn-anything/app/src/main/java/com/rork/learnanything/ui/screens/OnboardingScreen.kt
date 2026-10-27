package com.rork.learnanything.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Auth
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA

private val TOPICS = listOf("Math", "Science", "English", "Cars & engines", "Mechanics", "AI", "Coding", "Business", "History", "Music", "Languages", "Anything else")
private val STYLES = listOf("Visual", "Examples", "Step by step", "Practice first")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(nav: NavController) {
    val c = LA.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var style by rememberSaveable { mutableStateOf("Examples") }
    var minutes by rememberSaveable { mutableIntStateOf(20) }

    fun finish() {
        val finalGoal = goal.trim().ifBlank { picked.firstOrNull() ?: "Explore something new" }
        val interests = (picked + finalGoal.takeIf { it.isNotBlank() }.orEmpty()).distinct()
        AppStore.update {
            it.copy(profile = it.profile.copy(
                name = name.trim(),
                interests = interests,
                learningStyle = style,
                dailyGoalMinutes = minutes,
                onboarded = true,
            ))
        }
        AppStore.track("onboarding_completed", mapOf("goal" to finalGoal, "interests" to interests.joinToString()))
        val next = if (goal.isNotBlank()) Routes.intake(finalGoal) else null
        if (Auth.session.value != null) {
            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            if (next != null) nav.navigate(next)
        } else {
            // Always offer account creation after the short setup. If the backend is not configured,
            // AuthScreen explains this and offers local learning until the owner connects it.
            nav.navigate(Routes.auth("onboarding", next.orEmpty())) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).systemBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(48.dp))
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c.coral))
            }
            Spacer(Modifier.width(48.dp))
        }

        AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
        }, label = "onb") { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (st) {
                    0 -> {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)).background(c.hero), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = c.amber, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Learn anything. One step at a time.", color = c.onHero, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("What do you want to learn?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("Start with one goal. We’ll teach the fundamentals first, then build toward real skills with clear explanations and guided practice. No long setup questionnaire.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(
                            value = goal,
                            onValueChange = { goal = it.take(160) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Learn how car engines work") },
                            leadingIcon = { Icon(Icons.Rounded.MenuBook, null) },
                            shape = RoundedCornerShape(18.dp),
                            minLines = 2,
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Or start with a topic", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TOPICS.forEach { topic ->
                                FilterChip(
                                    selected = topic in picked,
                                    onClick = {
                                        if (topic in picked) picked.remove(topic)
                                        else {
                                            picked.clear()
                                            picked.add(topic)
                                            if (goal.isBlank()) goal = topic
                                        }
                                    },
                                    label = { Text(topic) },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                    1 -> {
                        Spacer(Modifier.height(30.dp))
                        Text("What should we call you?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("This helps make your lessons feel personal. You can change it later.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(30) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Your first name") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        Spacer(Modifier.height(20.dp))
                        LACard(Modifier.fillMaxWidth(), color = c.surfaceAlt, borderColor = c.border) {
                            Text("Your first goal", style = MaterialTheme.typography.labelLarge, color = c.inkSoft)
                            Spacer(Modifier.height(4.dp))
                            Text(goal.ifBlank { "Explore something new" }, style = MaterialTheme.typography.titleLarge, color = c.ink)
                        }
                    }
                    else -> {
                        Spacer(Modifier.height(24.dp))
                        Text("Make learning fit your day", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("Choose what works for you. You can change these settings any time.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(22.dp))
                        Text("How do you learn best?", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            STYLES.forEach { option ->
                                FilterChip(selected = style == option, onClick = { style = option }, label = { Text(option) }, shape = CircleShape)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Daily learning goal", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Spacer(Modifier.height(6.dp))
                        Text("Small, consistent sessions work best.", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 15, 20, 30, 45).forEach { m ->
                                FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }, shape = CircleShape)
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(24.dp)) {
            PrimaryButton(
                "Build my learning path",
                { finish() },
                Modifier.fillMaxWidth(),
                enabled = goal.isNotBlank() || picked.isNotEmpty(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Next: create a free account to save your progress. You can personalize your learning preferences later.",
                style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
