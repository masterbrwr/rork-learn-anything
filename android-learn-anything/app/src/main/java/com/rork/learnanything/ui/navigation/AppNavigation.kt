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
import com.rork.learnanything.ui.components.SparkleMark
import com.rork.learnanything.ui.components.PrimaryButton
import com.rork.learnanything.ui.screens.*
import com.rork.learnanything.ui.theme.LA
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector?)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val c = LA.colors
    val onboarded = remember { AppStore.state.value.profile.onboarded }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.TABS

    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
        Tab(Routes.PATHS, "Paths", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
        Tab(Routes.TUTOR, "Tutor", Icons.Outlined.Home, null),
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
                        NavigationBar(containerColor = c.canvas, tonalElevation = 0.dp) {
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
                                        if (t.selectedIcon == null) SparkleMark(Modifier.size(22.dp), if (selected) c.coral else c.inkSoft)
                                        else Icon(if (selected) t.selectedIcon else t.icon, null)
                                    },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = c.coral, selectedTextColor = c.coral, indicatorColor = c.coralSoft,
                                        unselectedIconColor = c.inkSoft, unselectedTextColor = c.inkSoft,
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
                composable(Routes.TUTOR) { TutorScreen(nav) }
                composable(Routes.STUDY) { StudyScreen(nav) }
                composable(Routes.PROFILE) { ProfileScreen(nav) }
                composable(Routes.PRICING) { PricingScreen(nav) }
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
