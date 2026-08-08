package com.neoutils.finsight.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.SyncExchangeRatesUseCase
import com.neoutils.finsight.extension.ProvidePlatformContext
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.ProvideNavController
import com.neoutils.finsight.ui.component.DetailPaneHost
import com.neoutils.finsight.ui.component.FormattingLocalsHost
import com.neoutils.finsight.ui.component.ModalManagerHost
import com.neoutils.finsight.ui.component.SharedTransitionProvider
import com.neoutils.finsight.ui.screen.home.ChromeHost
import com.neoutils.finsight.ui.theme.FinsightTheme
import com.neoutils.finsight.ui.util.exposeTestTags
import org.koin.compose.koinInject

@Composable
fun App() {
    val analytics = koinInject<Analytics>()
    val crashlytics = koinInject<Crashlytics>()
    val authService = koinInject<AuthService>()
    val syncExchangeRates = koinInject<SyncExchangeRatesUseCase>()

    // The app's cross-cutting, fired and forgotten.
    LaunchedEffect(Unit) {
        val userId = authService.getUserId()
        analytics.setUserId(userId)
        crashlytics.setUserId(userId)
    }

    // **The app's first real initialisation step**, and it is born harmless by
    // construction: nothing in the composition awaits it, no screen observes it, and
    // failing means writing nothing. It is here and not in `DashboardViewModel` because a
    // rate is not the dashboard's business — tying it to a tab would make the upkeep
    // depend on which screen the user happened to open (design D8).
    //
    // **When** it is owed is not decided here: `whenDue()` is the upkeep's own rule, and
    // the shell only collects it. That is deliberate — the trigger has to fire when the
    // base currency changes, and a shell that named the base to find that out would be the
    // first screen in the app to name it, which the reach guard exists to stop.
    //
    // This is **not** the sync command the design refuses (design D8c). What that refuses
    // is a chore the user has to remember and a surface that waits on the network while
    // they watch; this is a state change of the app, and nobody awaits it.
    LaunchedEffect(Unit) {
        syncExchangeRates.whenDue().collect { syncExchangeRates() }
    }

    FinsightTheme {
        // The E2E driver reads test tags off the accessibility tree, and only a composition root
        // can publish them. This is the app window's root; a modal sheet opens its own.
        Surface(modifier = Modifier.exposeTestTags()) {
            ProvidePlatformContext {
                FormattingLocalsHost {
                    ProvideNavController {
                        ModalManagerHost {
                            DetailPaneHost {
                                // The layout wraps the shell, so the chrome can declare itself
                                // in the transition overlay — above any shared element.
                                SharedTransitionProvider {
                                    ChromeHost { paddingValues ->
                                        AppNavHost(
                                            modifier = Modifier.padding(paddingValues),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
