package com.rork.learnanything

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.learnanything.data.AppStore
import com.rork.learnanything.data.Auth
import com.rork.learnanything.data.Billing
import com.rork.learnanything.ui.navigation.AppNavigation
import com.rork.learnanything.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStore.init(applicationContext)
        Auth.init(applicationContext)
        com.rork.learnanything.data.Narrator.init(applicationContext)
        Billing.init(applicationContext)
        Auth.handleRedirect(intent?.data)
        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) { Billing.refresh() }
            val state by AppStore.state.collectAsStateWithLifecycle()
            AppTheme(state.themeMode) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Auth.handleRedirect(intent.data)
    }
}
