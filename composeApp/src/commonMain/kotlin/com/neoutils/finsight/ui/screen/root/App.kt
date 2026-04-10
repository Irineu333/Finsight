package com.neoutils.finsight.ui.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.ui.theme.FinsightTheme
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.koin.compose.koinInject

@Composable
fun App() {
    val analytics = koinInject<Analytics>()

    LaunchedEffect(Unit) {
        val auth = Firebase.auth
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }
        analytics.setUserId(auth.currentUser?.uid)
    }

    FinsightTheme {
        AppNavHost()
    }
}
