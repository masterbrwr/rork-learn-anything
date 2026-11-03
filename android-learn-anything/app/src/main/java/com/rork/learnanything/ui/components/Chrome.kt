package com.rork.learnanything.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rork.learnanything.ui.theme.LA

/** Modern detail-screen chrome shared by lessons, quizzes, exams, notes and other focused views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = LA.colors.canvas,
        topBar = {
            Surface(
                color = LA.colors.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                            }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = LA.colors.surface,
                            scrolledContainerColor = LA.colors.surface,
                            titleContentColor = LA.colors.ink,
                            navigationIconContentColor = LA.colors.ink,
                            actionIconContentColor = LA.colors.inkSoft,
                        ),
                    )
                    HorizontalDivider(color = LA.colors.border.copy(alpha = 0.7f))
                }
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun TopLevelScaffold(
    nav: NavController,
    title: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    DetailScaffold(title = title, onBack = { nav.popBackStack() }, content = content)
}
