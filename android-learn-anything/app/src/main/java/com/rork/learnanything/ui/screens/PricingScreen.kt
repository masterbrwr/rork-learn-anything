package com.rork.learnanything.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Billing
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private data class LimitRow(
    val title: String,
    val free: String,
    val premium: String,
    val voice: Boolean = false,
)

@Composable
fun PricingScreen(nav: NavController, welcome: Boolean = false, reason: String = "") {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Billing.refresh() }

    fun restore() {
        busy = true
        scope.launch {
            when (val r = Billing.restore()) {
                Billing.Result.Success -> Toast.makeText(ctx, "Premium is active.", Toast.LENGTH_SHORT).show()
                is Billing.Result.Failed -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                else -> Unit
            }
            busy = false
        }
    }

    fun subscribe() {
        val activity = ctx.findActivity() ?: return
        busy = true
        scope.launch {
            when (val r = Billing.startSubscription(activity)) {
                Billing.Result.Started -> Toast.makeText(ctx, "Continue in PayPal to finish Premium.", Toast.LENGTH_LONG).show()
                is Billing.Result.Failed -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                else -> Unit
            }
            busy = false
        }
    }

    val rows = listOf(
        LimitRow("AI tutor", "10 questions/day", "Unlimited"),
        LimitRow("Learning paths", "1 active path", "Unlimited"),
        LimitRow("Interactive lessons", "3 lessons/day", "Unlimited"),
        LimitRow("Quizzes & practice", "5 activities/day", "Unlimited"),
        LimitRow("Flashcards", "10 reviews/day", "Unlimited"),
        LimitRow("Notes & bookmarks", "20 saved items", "Unlimited"),
        LimitRow("Exam prep & mock tests", "1 test/week", "Unlimited"),
        LimitRow("Step-by-step practical guides", "1 guide/day", "Unlimited"),
        LimitRow("Personalized plans", "Basic", "Advanced + adaptive"),
        LimitRow("Progress & recommendations", "Basic", "Advanced"),
        LimitRow("Voice tutor", "Unlimited", "Unlimited", voice = true),
    )

    val reasonTitle = when (reason) {
        "tutor" -> "You've reached today's AI tutor limit"
        "paths" -> "You've reached your Free learning-path limit"
        else -> "You're at a Free-tier limit"
    }
    val reasonBody = when (reason) {
        "tutor" -> "You used all 10 Free tutor questions today. Premium removes the daily cap."
        "paths" -> "Free includes 1 active learning path. Premium gives you unlimited paths."
        else -> "Upgrade to keep going without the Free-tier limits."
    }

    DetailScaffold(title = "Premium", onBack = { nav.popBackStack() }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (s.isPremium) {
                PrimaryButton("Continue learning", { nav.popBackStack() }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    "Start Premium · $6.99/month",
                    { subscribe() },
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                    loading = busy,
                )
                TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(if (welcome) "Continue with Free" else "Keep using Free", color = c.inkSoft)
                }
            }
            TextButton(onClick = { restore() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Check Premium status", color = c.inkSoft)
            }
            Text(
                "$6.99/month. Cancel anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkFaint,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            if (!s.isPremium && (welcome || reason.isNotBlank())) {
                LACard(Modifier.fillMaxWidth(), color = c.coralSoft, borderColor = c.coral.copy(alpha = .25f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.coral, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (welcome) "Start with Premium" else reasonTitle, style = MaterialTheme.typography.titleMedium, color = c.ink, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(if (welcome) "Unlock Premium for $6.99/month, or continue with the Free version below." else reasonBody, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (s.isPremium) {
                LACard(Modifier.fillMaxWidth(), color = c.successSoft, borderColor = c.success.copy(alpha = .3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = c.success)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Premium is active", style = MaterialTheme.typography.titleMedium, color = c.ink, fontWeight = FontWeight.Bold)
                            Text("All learning limits are lifted.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            Text("Learn anything. Go further.", style = MaterialTheme.typography.headlineLarge, color = c.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(
                "Free gives you a real taste of Learn Anything. Premium removes the limits so you can learn as much as you want.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(18.dp))

            LACard(Modifier.fillMaxWidth(), color = c.ink.copy(alpha = .035f), borderColor = c.border) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(c.successSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Learn Anything Premium", style = MaterialTheme.typography.titleLarge, color = c.ink, fontWeight = FontWeight.Bold)
                        Text("$6.99/month", style = MaterialTheme.typography.titleMedium, color = c.inkSoft)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Unlimited personalized learning, AI tutoring, quizzes, step-by-step lessons, and premium learning features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("What you get", style = MaterialTheme.typography.titleLarge, color = c.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            LACard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Feature", Modifier.weight(1f), color = c.inkSoft, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("FREE", Modifier.width(112.dp), textAlign = TextAlign.Center, color = c.inkSoft, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("PREMIUM", Modifier.width(112.dp), textAlign = TextAlign.Center, color = c.success, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.border)

                rows.forEachIndexed { index, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, color = c.ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (row.voice) {
                                Text("Always included", color = c.success, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(row.free, Modifier.width(112.dp), textAlign = TextAlign.Center, color = if (row.voice) c.success else c.inkSoft, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.width(112.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(row.premium, color = c.ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (index != rows.lastIndex) HorizontalDivider(color = c.border.copy(alpha = .55f))
                }
            }

            Spacer(Modifier.height(16.dp))
            LACard(Modifier.fillMaxWidth(), color = c.ink.copy(alpha = .025f), borderColor = c.border) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Free stays useful", color = c.ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "You can still explore the app and use unlimited voice learning. The daily and weekly limits reset automatically.",
                            color = c.inkSoft,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
