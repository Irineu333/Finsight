package com.neoutils.finsight.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.SyncExchangeRatesUseCase
import com.neoutils.finsight.extension.ProvidePlatformContext
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
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
    val periodicBackup = koinInject<PeriodicBackup>()

    // The app's cross-cutting, fired and forgotten — and forgotten means the app opens
    // whether or not it succeeds. Resolving the id fails for reasons that have nothing to
    // do with finance, so the failure is a value here and the only thing to do with it is
    // record it: an anonymous id labels telemetry, and losing it costs telemetry.
    LaunchedEffect(Unit) {
        authService.getUserId().fold(
            ifLeft = { crashlytics.recordException(it.cause) },
            ifRight = { userId ->
                analytics.setUserId(userId)
                crashlytics.setUserId(userId)
            },
        )
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

    // **The app being opened, told to whoever wants to know it.** This is the whole of the
    // periodic trigger's cadence, and it is here for the reason the upkeep above is: there
    // is no `ProcessLifecycleOwner` in this app and nothing runs while it is closed — a
    // promise of "every N days" is one no supported platform lets an app keep (design D5) —
    // so the shell is the only place that can say "opened", and it says nothing else.
    //
    // **Whether a copy is owed is not decided here.** The switch, the interval and whether
    // anything was even entered since the last copy all live behind `captureIfDue()`; the
    // shell would be a second place they could be answered differently.
    //
    // The frame is waited for on purpose. The effect starts as the first composition is
    // applied, and a `VACUUM INTO` of the whole archive would then be competing with the
    // first thing the user sees; resuming on a frame callback puts it after that frame, and
    // the capture itself leaves this dispatcher. Nothing awaits it, and it does not throw.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        periodicBackup.captureIfDue()
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
