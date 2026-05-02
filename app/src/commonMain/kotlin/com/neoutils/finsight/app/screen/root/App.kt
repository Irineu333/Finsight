package com.neoutils.finsight.app.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.auth.AuthService
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.theme.FinsightTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    val analytics = koinInject<Analytics>()
    val crashlytics = koinInject<Crashlytics>()
    val authService = koinInject<AuthService>()

    LaunchedEffect(Unit) {
        val userId = authService.getUserId()
        analytics.setUserId(userId)
        crashlytics.setUserId(userId)
    }

    FinsightTheme {
        AppNavHost()
    }
}
