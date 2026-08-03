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
import com.neoutils.finsight.domain.repository.ICurrencyRepository
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
import org.koin.compose.koinInject

@Composable
fun App() {
    val analytics = koinInject<Analytics>()
    val crashlytics = koinInject<Crashlytics>()
    val authService = koinInject<AuthService>()
    val syncExchangeRates = koinInject<SyncExchangeRatesUseCase>()
    val currencyRepository = koinInject<ICurrencyRepository>()

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
    // It runs on launch **and** whenever the registry gains a currency, so that
    // registering one and using it does not wait a day. That is safe precisely because the
    // daily bound is per currency: everything already answered today is skipped, so a
    // re-run costs the currency that is actually new and nothing else (design D8b).
    //
    // This is **not** the sync command the design refuses (design D8c). What that refuses
    // is a chore the user has to remember and a surface that waits on the network while
    // they watch; this is a state change of the app, and nobody awaits it.
    LaunchedEffect(Unit) {
        currencyRepository.observeOffered().collect { syncExchangeRates() }
    }

    FinsightTheme {
        Surface {
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
