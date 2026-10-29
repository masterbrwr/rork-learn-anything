package com.rork.learnanything.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun PricingScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val ctx = LocalContext.current
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

    DetailScaffold(title = "Premium", onBack = { nav.popBackStack() }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(20.dp, 12.dp)) {
            if (s.isPremium) {
                PrimaryButton("Continue learning", { nav.popBackStack() }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Start Premium · $6.99/month", { subscribe() }, Modifier.fillMaxWidth(), enabled = !busy, loading = busy)
            }
            TextButton(onClick = { restore() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Check Premium status", color = c.inkSoft) }
            Text("7-day free trial, then $6.99/month. Cancel anytime.", style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            if (s.isPremium) {
                LACard(Modifier.fillMaxWidth(), color = c.successSoft, borderColor = c.success.copy(alpha = .3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = c.success)
                        Spacer(Modifier.width(10.dp))
                        Column { Text("Premium is active", style = MaterialTheme.typography.titleMedium, color = c.ink); Text("Every limit is lifted.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft) }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            Text("Learn anything, without limits.", style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text("Free gets you far. Premium removes every ceiling.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
            Spacer(Modifier.height(18.dp))
            LACard(Modifier.fillMaxWidth()) {
                Text("Learn Anything Premium", style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text("7 days free, then $6.99/month", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                Spacer(Modifier.height(14.dp))
                listOf("Unlimited AI tutor conversations", "Unlimited personalized learning paths", "Step-by-step practical tutorials", "Advanced quizzes and practice", "Voice tutor and learning tools", "Personalized progress and recommendations").forEach { feature ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(feature, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            LACard(Modifier.fillMaxWidth()) {
                Text("Free vs Premium", style = MaterialTheme.typography.titleLarge, color = c.ink)
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.border)
                listOf(
                    Triple("AI tutor messages", "${AppStore.FREE_TUTOR_DAILY}/day", "Unlimited"),
                    Triple("AI learning paths", "${AppStore.FREE_PATHS}", "Unlimited"),
                    Triple("Interactive lessons", "Yes", "Yes"),
                    Triple("Flashcards & notes", "Yes", "Yes"),
                    Triple("Exam prep & mock tests", "Yes", "Yes"),
                    Triple("Voice tutor", "Yes", "Yes"),
                ).forEach { (feature, free, premium) ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(feature, Modifier.weight(1f), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                        Text(free, Modifier.width(72.dp), color = c.inkSoft, style = MaterialTheme.typography.bodyMedium)
                        Text(premium, Modifier.width(84.dp), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
