package com.rork.learnanything.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Auth
import com.rork.learnanything.ui.components.PrimaryButton
import com.rork.learnanything.ui.components.SparkleMark
import com.rork.learnanything.ui.screens.*
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val c = LA.colors
    val onboarded = remember { AppStore.state.value.profile.onboarded }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.TABS
    val session by Auth.session.collectAsStateWithLifecycle()
    val appState by AppStore.state.collectAsStateWithLifecycle()
    var previousSessionId by remember { mutableStateOf(session?.user?.id) }
    var showWelcomePremium by remember { mutableStateOf(false) }
    var showLimitPremium by remember { mutableStateOf(false) }
    var dismissedLimitKey by remember { mutableStateOf<String?>(null) }

    val limitReason = when {
        !appState.isPremium && route == Routes.TUTOR && AppStore.tutorRemaining(appState) <= 0 -> "tutor"
        !appState.isPremium && route?.startsWith("intake") == true && appState.courses.count { !it.id.startsWith("seed") } >= AppStore.FREE_PATHS -> "paths"
        else -> ""
    }
    val limitKey = if (limitReason.isBlank()) null else "$route:$limitReason"

    LaunchedEffect(route) {
        dismissedLimitKey = null
        showLimitPremium = false
    }

    LaunchedEffect(limitKey) {
        if (limitKey != null && limitKey != dismissedLimitKey) showLimitPremium = true
    }

    LaunchedEffect(session?.user?.id, appState.isPremium) {
        val currentId = session?.user?.id
        if (currentId != null && previousSessionId == null && !appState.isPremium) showWelcomePremium = true
        previousSessionId = currentId
    }

    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
        Tab(Routes.PATHS, "Courses", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
        Tab(Routes.MATERIALS, "Materials", Icons.Rounded.FolderOpen, Icons.Rounded.FolderOpen),
        Tab(Routes.TUTOR, "Tutor", Icons.Rounded.AutoAwesome, Icons.Rounded.AutoAwesome),
        Tab(Routes.STUDY, "Study", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
        Tab(Routes.PROFILE, "Profile", Icons.Outlined.Person, Icons.Rounded.Person),
    )

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Scaffold(
            containerColor = c.canvas,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(showBar, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        HorizontalDivider(color = c.border)
                        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
                            tabs.forEach { t ->
                                val selected = entry?.destination?.hierarchy?.any { it.route == t.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        if (t.route == Routes.TUTOR) {
                                            Box(
                                                Modifier.size(32.dp).clip(CircleShape).background(if (selected) c.coralSoft else c.surfaceAlt),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Rounded.AutoAwesome, null, tint = if (selected) c.coral else c.inkSoft, modifier = Modifier.size(18.dp))
                                            }
                                        } else {
                                            Icon(if (selected) t.selectedIcon ?: t.icon else t.icon, null)
                                        }
                                    },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = c.coral,
                                        selectedTextColor = c.coral,
                                        indicatorColor = c.coralSoft,
                                        unselectedIconColor = c.inkSoft,
                                        unselectedTextColor = c.inkSoft,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { inner ->
            NavHost(
                navController = nav,
                startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING,
                modifier = Modifier.padding(inner).consumeWindowInsets(inner),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 12 } },
            ) {
                composable(Routes.ONBOARDING) { OnboardingScreen(nav) }
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.PATHS) { PathsScreen(nav) }
                composable(Routes.MATERIALS) { MaterialsScreen(nav) }
                composable(Routes.TUTOR) { TutorScreen(nav) }
                composable(Routes.STUDY) { StudyScreen(nav) }
                composable(Routes.PROFILE) { ProfileScreen(nav) }
                composable(
                    "pricing?welcome={welcome}&reason={reason}",
                    listOf(
                        navArgument("welcome") { defaultValue = "false" },
                        navArgument("reason") { defaultValue = "" },
                    ),
                ) {
                    PricingScreen(
                        nav,
                        welcome = it.arguments?.getString("welcome") == "true",
                        reason = it.arguments?.getString("reason").orEmpty(),
                    )
                }
                composable(Routes.NOTES) { NotesScreen(nav) }
                composable(Routes.MISTAKES) { MistakesScreen(nav) }
                composable("intake?goal={goal}", listOf(navArgument("goal") { defaultValue = "" })) {
                    GoalIntakeScreen(nav, it.arguments?.getString("goal").orEmpty())
                }
                composable("lesson/{courseId}/{lessonId}") {
                    LessonScreen(nav, it.arguments?.getString("courseId").orEmpty(), it.arguments?.getString("lessonId").orEmpty())
                }
                composable(
                    "session?topic={topic}&minutes={minutes}",
                    listOf(navArgument("topic") { defaultValue = "" }, navArgument("minutes") { type = NavType.IntType; defaultValue = 15 }),
                ) { SessionScreen(nav, it.arguments?.getString("topic").orEmpty(), it.arguments?.getInt("minutes") ?: 15) }
                composable(
                    "quiz?mode={mode}&topic={topic}&examId={examId}",
                    listOf(navArgument("mode") { defaultValue = "quiz" }, navArgument("topic") { defaultValue = "" }, navArgument("examId") { defaultValue = "" }),
                ) { QuizScreen(nav, it.arguments?.getString("mode") ?: "quiz", it.arguments?.getString("topic").orEmpty(), it.arguments?.getString("examId").orEmpty()) }
                composable("flashcards?generate={generate}", listOf(navArgument("generate") { defaultValue = "" })) {
                    FlashcardsScreen(nav, it.arguments?.getString("generate").orEmpty())
                }
                composable("deck/{id}") { DeckScreen(nav, it.arguments?.getString("id").orEmpty()) }
                composable("review/{deckId}") { ReviewScreen(nav, it.arguments?.getString("deckId") ?: "all") }
                composable("note/{id}") { NoteEditorScreen(nav, it.arguments?.getString("id") ?: "new") }
                composable("auth?from={from}&next={next}", listOf(navArgument("from") { defaultValue = "" }, navArgument("next") { defaultValue = "" })) {
                    AuthScreen(nav, it.arguments?.getString("from") == "onboarding", it.arguments?.getString("next").orEmpty())
                }
                composable("exam?id={id}", listOf(navArgument("id") { defaultValue = "" })) { ExamScreen(nav, it.arguments?.getString("id").orEmpty()) }
            }
        }
        AchievementOverlay()

        if (showWelcomePremium && !appState.isPremium) {
            ModalBottomSheet(
                onDismissRequest = { showWelcomePremium = false },
                containerColor = c.canvas,
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(c.coralSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.coral, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Welcome to Learn Anything", style = MaterialTheme.typography.headlineSmall, color = c.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Unlock unlimited learning with Premium — $6.99/month after a 7-day free trial.", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("See Premium & start trial", {
                        showWelcomePremium = false
                        nav.navigate(Routes.pricing(welcome = true))
                    }, Modifier.fillMaxWidth())
                    TextButton(onClick = { showWelcomePremium = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue with Free", color = c.inkSoft)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (showLimitPremium && !appState.isPremium && limitReason.isNotBlank()) {
            ModalBottomSheet(
                onDismissRequest = {
                    dismissedLimitKey = limitKey
                    showLimitPremium = false
                },
                containerColor = c.canvas,
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(c.coralSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.coral, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (limitReason == "tutor") "You've used today's Free tutor questions" else "You've reached your Free learning-path limit",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.ink,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (limitReason == "tutor") "Premium gives you unlimited AI tutor questions, so you can keep learning right now."
                        else "Premium gives you unlimited learning paths instead of the 1-path Free limit.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.inkSoft,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("See Premium", {
                        showLimitPremium = false
                        dismissedLimitKey = limitKey
                        nav.navigate(Routes.pricing(reason = limitReason))
                    }, Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        dismissedLimitKey = limitKey
                        showLimitPremium = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue with Free", color = c.inkSoft)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Celebrates newly unlocked achievements with a springy card, one at a time. */
@Composable
private fun AchievementOverlay() {
    val queue by AppStore.unlockQueue.collectAsStateWithLifecycle()
    val current = queue.firstOrNull()
    val c = LA.colors
    val haptic = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(current?.id) {
        if (current != null) {
            visible = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(3200)
            visible = false
            delay(300)
            AppStore.consumeUnlock()
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible && current != null,
            enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { -it } + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.8f) + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val a = current ?: return@AnimatedVisibility
            Surface(shape = MaterialTheme.shapes.extraLarge, color = c.hero, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(c.amber.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(achievementIcon(a.icon), null, tint = c.amber, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ACHIEVEMENT UNLOCKED", style = MaterialTheme.typography.labelSmall, color = c.amber)
                        Text(a.title, style = MaterialTheme.typography.titleLarge, color = c.onHero)
                        Text(a.detail, style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
