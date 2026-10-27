package com.rork.learnanything.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.rork.learnanything.data.Auth
import com.rork.learnanything.ui.components.*
import com.rork.learnanything.ui.navigation.Routes
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthStep { Choose, Email, Phone, Code }

/** Sign in / create account. [fromOnboarding] lets people skip and keep learning locally. */
@Composable
fun AuthScreen(nav: NavController, fromOnboarding: Boolean, next: String = "") {
    val c = LA.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val session by Auth.session.collectAsStateWithLifecycle()
    val oauthError by Auth.oauthError.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableStateOf(AuthStep.Choose) }
    var creating by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("+1") }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var providers by remember { mutableStateOf(setOf("email")) }
    var resendIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { providers = Auth.enabledProviders().ifEmpty { setOf("email") } }
    LaunchedEffect(oauthError) { oauthError?.let { error = it; busy = false; Auth.oauthError.value = null } }
    LaunchedEffect(resendIn) { if (resendIn > 0) { delay(1000); resendIn-- } }

    fun leave() {
        if (fromOnboarding) {
            nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
            if (next.isNotBlank()) nav.navigate(next)
        } else nav.popBackStack()
    }
    // Signed in (including after the Google browser round-trip) → continue.
    LaunchedEffect(session?.user?.id) {
        if (session != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            leave()
        }
    }

    val fullPhone = country.trim().let { if (it.startsWith("+")) it else "+$it" } + phone.filter { it.isDigit() }

    fun run(block: suspend () -> Unit) {
        error = null; busy = true
        scope.launch {
            try { block() } catch (e: Exception) { error = e.message ?: "Something went wrong." } finally { busy = false }
        }
    }

    fun back() { error = null; step = when (step) { AuthStep.Code -> AuthStep.Phone; AuthStep.Choose -> AuthStep.Choose; else -> AuthStep.Choose } }
    BackHandler(enabled = step != AuthStep.Choose) { back() }

    Column(Modifier.fillMaxSize().background(c.canvas).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step != AuthStep.Choose) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.ink) }
            else if (!fromOnboarding) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, "Close", tint = c.ink) }
            Spacer(Modifier.weight(1f))
            if (fromOnboarding && step == AuthStep.Choose) TextButton(onClick = { leave() }) { Text("Skip for now", color = c.inkSoft) }
        }

        AnimatedContent(
            step, Modifier.weight(1f),
            transitionSpec = {
                val fwd = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (fwd) it / 5 else -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { if (fwd) -it / 5 else it / 5 } + fadeOut())
            },
            label = "auth",
        ) { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (st) {
                    AuthStep.Choose -> {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(c.hero), contentAlignment = Alignment.Center) {
                            AuthHeroArt()
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(if (fromOnboarding) "Keep your progress safe." else "Sign in to Learn Anything", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("Your paths, streaks, notes and flashcards are backed up and follow you to any phone.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(28.dp))
                        // Free sign-in only: email always; Google appears once its (free) OAuth keys are configured.
                        // Phone/SMS is intentionally not offered because texting codes costs money per message.
                        ProviderButton("Continue with email", leading = { Icon(Icons.Rounded.Email, null, tint = c.ink) }, onClick = { error = null; creating = true; step = AuthStep.Email }, enabled = !busy)
                        if ("google" in providers) {
                            Spacer(Modifier.height(10.dp))
                            ProviderButton(
                                label = "Continue with Google", leading = { GoogleMark() },
                                onClick = { runCatching { busy = true; Auth.startOAuth(ctx, "google") }.onFailure { busy = false; error = it.message } },
                                enabled = !busy,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        ProviderButton("I already have an account", leading = { Icon(Icons.Rounded.Person, null, tint = c.ink) }, onClick = { error = null; creating = false; step = AuthStep.Email }, enabled = !busy)
                    }

                    AuthStep.Email -> {
                        Spacer(Modifier.height(8.dp))
                        Text(if (creating) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(16.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(true to "Create account", false to "Sign in").forEachIndexed { i, (v, l) ->
                                SegmentedButton(selected = creating == v, onClick = { creating = v; error = null }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        if (creating) {
                            AuthField(name, { name = it.take(40) }, "Name", Icons.Rounded.Person, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                            Spacer(Modifier.height(12.dp))
                        }
                        AuthField(email, { email = it.trim() }, "Email", Icons.Rounded.Email, KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                        Spacer(Modifier.height(12.dp))
                        var show by remember { mutableStateOf(false) }
                        AuthField(
                            password, { password = it }, if (creating) "Password (8+ characters)" else "Password", Icons.Rounded.Lock,
                            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password", tint = c.inkSoft) } },
                        )
                    }

                    AuthStep.Phone -> {
                        Spacer(Modifier.height(8.dp))
                        Text("What's your number?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("We'll text you a 6-digit code. Standard message rates may apply.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(22.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AuthField(country, { v -> country = ("+" + v.filter { it.isDigit() }).take(5) }, "Code", null, KeyboardOptions(keyboardType = KeyboardType.Phone), Modifier.width(92.dp))
                            AuthField(phone, { v -> phone = v.filter { it.isDigit() || it == ' ' }.take(16) }, "Phone number", Icons.Rounded.PhoneIphone, KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done), Modifier.weight(1f))
                        }
                    }

                    AuthStep.Code -> {
                        Spacer(Modifier.height(8.dp))
                        Text("Enter the code", style = MaterialTheme.typography.headlineLarge, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("Sent to $fullPhone", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                        Spacer(Modifier.height(26.dp))
                        CodeBoxes(code) { v ->
                            code = v
                            if (v.length == 6 && !busy) run { Auth.verifyPhoneCode(fullPhone, v) }
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = { run { Auth.sendPhoneCode(fullPhone); resendIn = 30; code = "" } },
                            enabled = resendIn == 0 && !busy,
                        ) { Text(if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code", color = if (resendIn > 0) c.inkFaint else c.cobalt) }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.coralSoft).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = c.ink) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (step) {
                AuthStep.Choose -> Text(
                    "By continuing you agree to the Terms and acknowledge the Privacy Policy. You can delete your account anytime in Profile.",
                    style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                AuthStep.Email -> PrimaryButton(
                    if (creating) "Create account" else "Sign in",
                    { run { if (creating) Auth.signUpEmail(name, email, password) else Auth.signInEmail(email, password) } },
                    Modifier.fillMaxWidth(),
                    enabled = email.contains("@") && password.length >= (if (creating) 8 else 1) && (!creating || name.isNotBlank()),
                    loading = busy,
                )
                AuthStep.Phone -> PrimaryButton(
                    "Send code",
                    { run { Auth.sendPhoneCode(fullPhone); code = ""; resendIn = 30; step = AuthStep.Code } },
                    Modifier.fillMaxWidth(),
                    enabled = phone.count { it.isDigit() } >= 6 && country.length >= 2,
                    loading = busy,
                )
                AuthStep.Code -> PrimaryButton(
                    "Verify", { run { Auth.verifyPhoneCode(fullPhone, code) } }, Modifier.fillMaxWidth(),
                    enabled = code.length == 6, loading = busy,
                )
            }
        }
    }
}

@Composable
private fun ProviderButton(label: String, leading: @Composable () -> Unit, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LA.colors
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, c.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = c.ink),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Spacer(Modifier.width(36.dp))
    }
}

/** Neutral "G" monogram; we don't redraw the trademarked logo. */
@Composable
private fun GoogleMark() {
    val c = LA.colors
    Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, c.ink, CircleShape), contentAlignment = Alignment.Center) {
        Text("G", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.ink)
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: ImageVector?,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier.fillMaxWidth(),
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LA.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(label) }, singleLine = true,
        leadingIcon = icon?.let { { Icon(it, null, tint = c.inkSoft) } },
        trailingIcon = trailing,
        visualTransformation = visual, keyboardOptions = keyboard,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
            focusedBorderColor = c.ink, unfocusedBorderColor = c.border, cursorColor = c.coral,
            focusedLabelColor = c.ink,
        ),
    )
}

/** Six-box one-time-code input backed by a single hidden text field. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    val c = LA.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = code,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
        modifier = Modifier.focusRequester(focus),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions.Default,
        cursorBrush = SolidColor(c.coral),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val ch = code.getOrNull(i)?.toString() ?: ""
                    val active = i == code.length
                    Box(
                        Modifier.weight(1f).aspectRatio(0.82f).clip(RoundedCornerShape(14.dp)).background(c.surface)
                            .border(if (active) 2.dp else 1.dp, if (active) c.coral else c.border, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, style = MaterialTheme.typography.headlineMedium, color = c.ink) }
                }
            }
        },
    )
}

/** Line-art: an open book whose pages turn into a connected path of nodes. */
@Composable
private fun AuthHeroArt() {
    val c = LA.colors
    androidx.compose.foundation.Canvas(Modifier.size(width = 240.dp, height = 140.dp)) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val line = c.onHero.copy(alpha = 0.85f)
        // book
        val book = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.38f, h * 0.78f, w * 0.22f, h * 0.78f, w * 0.1f, h * 0.84f)
            lineTo(w * 0.1f, h * 0.42f)
            cubicTo(w * 0.22f, h * 0.36f, w * 0.38f, h * 0.36f, w * 0.5f, h * 0.46f)
            cubicTo(w * 0.62f, h * 0.36f, w * 0.78f, h * 0.36f, w * 0.9f, h * 0.42f)
            lineTo(w * 0.9f, h * 0.84f)
            cubicTo(w * 0.78f, h * 0.78f, w * 0.62f, h * 0.78f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(book, line, style = stroke)
        drawLine(line, androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.46f), androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.88f), 2.dp.toPx())
        // path of nodes rising from the book
        val pts = listOf(0.5f to 0.36f, 0.62f to 0.22f, 0.78f to 0.16f, 0.9f to 0.06f)
        for (i in 0 until pts.size - 1) {
            drawLine(
                line.copy(alpha = 0.5f),
                androidx.compose.ui.geometry.Offset(w * pts[i].first, h * pts[i].second),
                androidx.compose.ui.geometry.Offset(w * pts[i + 1].first, h * pts[i + 1].second),
                1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
        pts.forEachIndexed { i, (x, y) ->
            val o = androidx.compose.ui.geometry.Offset(w * x, h * y)
            if (i == pts.lastIndex) drawCircle(c.coral, 6.dp.toPx(), o) else drawCircle(line, 4.dp.toPx(), o, style = stroke)
        }
    }
}
