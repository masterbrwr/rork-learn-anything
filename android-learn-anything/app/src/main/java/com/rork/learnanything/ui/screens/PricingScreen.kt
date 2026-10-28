package com.rork.learnanything.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Billing
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.launch

private sealed interface OfferState {
    data object Loading : OfferState
    data class Ready(val packages: List<Package>) : OfferState
    data class Error(val message: String) : OfferState
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun Package.label() = when (packageType) {
    PackageType.MONTHLY -> "Monthly"
    PackageType.ANNUAL -> "Yearly"
    PackageType.LIFETIME -> "Lifetime"
    else -> product.title
}

private fun Package.cadence() = when (packageType) {
    PackageType.MONTHLY -> "per month"
    PackageType.ANNUAL -> "per year"
    PackageType.LIFETIME -> "one-time payment"
    else -> ""
}

private fun Package.ctaSuffix() = when (packageType) {
    PackageType.MONTHLY -> "/month"
    PackageType.ANNUAL -> "/year"
    else -> ""
}

@Composable
fun PricingScreen(nav: NavController) {
    val s by AppStore.state.collectAsStateWithLifecycle()
    val c = LA.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var offer by remember { mutableStateOf<OfferState>(OfferState.Loading) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        offer = OfferState.Loading
        offer = try {
            val o = Billing.currentOffering()
            val order = listOf(PackageType.MONTHLY, PackageType.ANNUAL, PackageType.LIFETIME)
            val pkgs = (o?.availablePackages ?: emptyList()).sortedBy { p -> order.indexOf(p.packageType).let { if (it < 0) 99 else it } }
            if (pkgs.isEmpty()) OfferState.Error("No plans are available right now.")
            else {
                if (selectedId == null || pkgs.none { it.identifier == selectedId }) {
                    selectedId = (pkgs.firstOrNull { it.packageType == PackageType.MONTHLY } ?: pkgs.first()).identifier
                }
                OfferState.Ready(pkgs)
            }
        } catch (e: Exception) {
            AppStore.logError("paywall_offering", e)
            OfferState.Error(e.message ?: "Something didn't load.")
        }
    }

    val packages = (offer as? OfferState.Ready)?.packages.orEmpty()
    val selected = packages.firstOrNull { it.identifier == selectedId }
    val monthly = packages.firstOrNull { it.packageType == PackageType.MONTHLY }

    fun restore() {
        busy = true
        scope.launch {
            when (val r = Billing.restore()) {
                Billing.Result.Success -> Toast.makeText(ctx, "Premium restored.", Toast.LENGTH_SHORT).show()
                is Billing.Result.Failed -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                Billing.Result.Cancelled -> Unit
            }
            busy = false
        }
    }

    DetailScaffold(title = "Premium", onBack = { nav.popBackStack() }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(20.dp, 12.dp)) {
            if (s.isPremium) {
                PrimaryButton("Continue learning", { nav.popBackStack() }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    text = selected?.let { "Continue · ${it.product.price.formatted}${it.ctaSuffix()}" } ?: "Continue",
                    onClick = {
                        val pkg = selected ?: return@PrimaryButton
                        val act = ctx.findActivity() ?: return@PrimaryButton
                        busy = true
                        AppStore.track("purchase_started", mapOf("package" to pkg.identifier))
                        scope.launch {
                            when (val r = Billing.purchase(act, pkg)) {
                                Billing.Result.Success -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(ctx, "Welcome to Premium.", Toast.LENGTH_SHORT).show()
                                }
                                is Billing.Result.Failed -> Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                                Billing.Result.Cancelled -> Unit
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected != null,
                    loading = busy,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { restore() }, enabled = !busy) { Text("Restore purchases", color = c.inkSoft) }
            }
            Text(
                "Subscriptions renew automatically until cancelled in Google Play.",
                style = MaterialTheme.typography.bodySmall, color = c.inkFaint,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }) { inner ->
        Column(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            if (s.isPremium) {
                LACard(Modifier.fillMaxWidth(), color = c.successSoft, borderColor = c.success.copy(alpha = 0.3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = c.success)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Premium is active", style = MaterialTheme.typography.titleMedium, color = c.ink)
                            Text("Every limit is lifted. Thanks for supporting Learn Anything.", style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            Text("Learn anything, without limits.", style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text("Free gets you far. Premium removes every ceiling.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
            Spacer(Modifier.height(18.dp))

            AnimatedContent(offer, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "offer") { st ->
                when (st) {
                    OfferState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(76.dp)) }
                    }
                    is OfferState.Error -> LACard(Modifier.fillMaxWidth()) {
                        Text("Something didn't load.", style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(st.message, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { reload++ }, shape = CircleShape) { Text("Retry") }
                    }
                    is OfferState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        st.packages.forEach { p ->
                            val badge = when {
                                p.packageType == PackageType.ANNUAL && monthly != null -> {
                                    val m = monthly.product.price.amountMicros * 12.0
                                    val y = p.product.price.amountMicros.toDouble()
                                    val pct = if (m > 0) ((1 - y / m) * 100).toInt() else 0
                                    if (pct > 0) "Save $pct%" else "Best value"
                                }
                                p.packageType == PackageType.LIFETIME -> "Pay once"
                                else -> null
                            }
                            PlanRow(p, badge, p.identifier == selectedId, enabled = !s.isPremium) { selectedId = p.identifier }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            LACard(Modifier.fillMaxWidth()) {
                Row { Text("", Modifier.weight(1f)); Text("Free", Modifier.width(72.dp), color = c.inkSoft, style = MaterialTheme.typography.labelLarge); Text("Premium", Modifier.width(84.dp), color = c.coral, style = MaterialTheme.typography.labelLarge) }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.border)
                listOf(
                    Triple("AI tutor messages", "${AppStore.FREE_TUTOR_DAILY}/day", "Unlimited"),
                    Triple("AI learning paths", "${AppStore.FREE_PATHS}", "Unlimited"),
                    Triple("Interactive lessons", "Yes", "Yes"),
                    Triple("Flashcards & notes", "Yes", "Yes"),
                    Triple("Exam prep & mock tests", "Yes", "Yes"),
                    Triple("Voice tutor", "Yes", "Yes"),
                ).forEach { (f, free, pro) ->
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f, Modifier.weight(1f), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                        Text(free, Modifier.width(72.dp), color = c.inkSoft, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.width(84.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text(pro, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlanRow(p: Package, badge: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LA.colors
    val border by animateColorAsState(if (selected) c.coral else c.border, label = "b")
    val bg by animateColorAsState(if (selected) c.coralSoft else c.surface, label = "bg")
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg).border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (selected) c.coral else c.inkFaint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label(), style = MaterialTheme.typography.titleMedium, color = c.ink)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = c.coral,
                        modifier = Modifier.clip(CircleShape).background(c.coral.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Text(p.cadence(), style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        }
        Text(p.product.price.formatted, style = MaterialTheme.typography.headlineSmall, color = c.ink)
    }
}
