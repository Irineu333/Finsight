package com.neoutils.finsight.ui.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.ui.theme.FinsightTheme
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
