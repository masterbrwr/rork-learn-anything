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

private val INTERESTS = listOf("Physics", "Biology", "Math", "Coding", "History", "Investing", "Engines", "Spanish", "Music theory", "Psychology", "Chemistry", "AI", "Writing", "Business", "Astronomy", "Geography")
private val STYLES = listOf("Visual", "Examples", "Step by step", "Practice first")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(nav: NavController) {
    val c = LA.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var style by rememberSaveable { mutableStateOf("Examples") }
    var minutes by rememberSaveable { mutableIntStateOf(20) }
    var goal by rememberSaveable { mutableStateOf("") }

    fun finish(goTo: String?) {
        AppStore.update { it.copy(profile = it.profile.copy(name = name.trim(), interests = picked.toList(), learningStyle = style, dailyGoalMinutes = minutes, onboarded = true)) }
        AppStore.track("onboarding_completed", mapOf("interests" to picked.joinToString()))
        if (Auth.session.value != null || !Auth.available) {
            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            if (goTo != null) nav.navigate(goTo)
        } else {
            nav.navigate(Routes.auth("onboarding", goTo.orEmpty())) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas).systemBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) IconButton(onClick = { step-- }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.ink) } else Spacer(Modifier.width(48.dp))
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= step) c.coral else c.border)) }
            }
            Spacer(Modifier.width(48.dp))
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut() }, label = "onb") { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (st) {
                    0 -> {
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)).background(c.hero), contentAlignment = Alignment.Center) {
                            EngineCylinder(250f, Modifier.size(180.dp), line = c.onHero)
                        }
                        Spacer(Modifier.height(28.dp))
                        Text("Learn anything.", style = MaterialTheme.typography.displaySmall, color = c.ink)
                        Spacer(Modifier.height(10.dp))
                        Text("Tell me what you want to learn. I'll build a path around what you already know, teach it with interactive lessons, and adapt every step.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                    }
                    1 -> {
                        Spacer(Modifier.height(24.dp))
                        Text("What should I call you?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), placeholder = { Text("Your first name") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    }
                    2 -> {
                        Spacer(Modifier.height(24.dp))
                        Text("What are you curious about?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Text("Pick a few. You can learn anything later.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(18.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            INTERESTS.forEach { i ->
                                FilterChip(selected = i in picked, onClick = { if (i in picked) picked.remove(i) else picked.add(i) }, label = { Text(i) }, shape = CircleShape)
                            }
                        }
                    }
                    else -> {
                        Spacer(Modifier.height(24.dp))
                        Text("How do you learn best?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(14.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { STYLES.forEach { s -> FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s) }) } }
                        Spacer(Modifier.height(20.dp))
                        Text("Daily time", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(10, 20, 30, 45).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }) } }
                        Spacer(Modifier.height(20.dp))
                        Text("Your first goal (optional)", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Understand how car engines work") }, shape = RoundedCornerShape(16.dp))
                    }
                }
            }
        }
        Column(Modifier.padding(24.dp)) {
            PrimaryButton(
                when (step) { 0 -> "Get started"; 3 -> if (goal.isNotBlank()) "Build my path" else "Start learning"; else -> "Continue" },
                { if (step < 3) step++ else finish(if (goal.isNotBlank()) Routes.intake(goal.trim()) else null) },
                Modifier.fillMaxWidth(),
                enabled = step != 1 || name.isNotBlank(),
            )
        }
    }
}
