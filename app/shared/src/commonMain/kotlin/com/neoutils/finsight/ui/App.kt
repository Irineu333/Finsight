package com.neoutils.finsight.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.auth.AuthService
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.SyncExchangeRatesUseCase
import com.neoutils.finsight.extension.ProvidePlatformContext
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.feature.mcp.api.toUiText
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.ProvideNavController
import com.neoutils.finsight.ui.component.DetailPaneHost
import com.neoutils.finsight.ui.component.FormattingLocalsHost
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.component.ModalManagerHost
import com.neoutils.finsight.ui.component.SharedTransitionProvider
import com.neoutils.finsight.ui.screen.home.ChromeHost
import com.neoutils.finsight.ui.theme.FinsightTheme
import com.neoutils.finsight.ui.util.exposeTestTags
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.koinInject

@Composable
fun App() {
    val analytics = koinInject<Analytics>()
    val crashlytics = koinInject<Crashlytics>()
    val authService = koinInject<AuthService>()
    val syncExchangeRates = koinInject<SyncExchangeRatesUseCase>()
    val mcpServer = koinInject<McpServerController>()
    val modalManager = koinInject<ModalManager>()
    val periodicBackup = koinInject<PeriodicBackup>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

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

    // **The one background failure in this app that has to interrupt.** A server the user switched
    // on and that did not come up shows its symptom somewhere else entirely — the agent will not
    // connect — and the person who has to act is the only one nobody tells. Waiting for a visit to
    // the server section would mean waiting for the user to suspect the app in the first place.
    //
    // This is the opposite call from the rate upkeep above, and deliberately: a quotation that did
    // not arrive changes nothing the user asked for, while a socket that did not bind silently
    // withdraws something they switched on. It is also the only channel in the app that reaches
    // whoever is not looking at the screen that owns the subject.
    //
    // Every failure is announced, including one that repeats, because each is a separate attempt
    // that did not come up.
    LaunchedEffect(Unit) {
        mcpServer.state
            .filterIsInstance<McpServerState.Failed>()
            .collect { failure -> modalManager.showError(failure.toUiText()) }
    }

    // **The app coming to the front, told to whoever wants to know it.** This is the whole
    // of the periodic trigger's cadence, and it is here for the reason the upkeep above is:
    // nothing runs while the app is not — a promise of "every N days" is one no supported
    // platform lets an app keep (design D5) — so the shell is the only place that can say
    // "opened", and it says nothing else.
    //
    // **An opening is a session, not a process**, and the lifecycle is the only thing that
    // tells the two apart. This function composes once per launch, so an effect keyed on
    // nothing asks the question once and never again however long the app stays up — which
    // on Desktop, where one window is composed for the whole run, is once per boot.
    //
    // **Every step towards the front is an occasion, and what a step is belongs to the
    // platform.** Android and iOS climb to `STARTED` when the app becomes visible and to
    // `RESUMED` when it becomes interactive; Compose for Desktop reaches `STARTED` when the
    // window is shown and derives `RESUMED` from window focus, so focus is the only signal
    // a desktop window that is never minimised ever gives. Asking on the way up rather than
    // at one chosen rung is what keeps both true at once: the app that is launched without
    // focus is still asked, and the window that is left open for a month is asked again
    // every time somebody comes back to it. Going the other way is never an occasion, so
    // leaving is silent.
    //
    // A repeated occasion costs a switch and a subtraction, which is why asking often is
    // affordable. The collector is sequential and belongs to the composition rather than to
    // any one occasion, so a second one arriving mid-capture waits for the first to finish —
    // by which time it is no longer due — and walking away does not cancel a copy that has
    // already started.
    //
    // **Whether a copy is owed is not decided here.** The switch, the interval and whether
    // anything was even entered since the last copy all live behind `captureIfDue()`; the
    // shell would be a second place they could be answered differently.
    //
    // The frame is waited for on purpose. The effect resumes as a composition is applied,
    // and a `VACUUM INTO` of the whole archive would then be competing with the first thing
    // the user sees; resuming on a frame callback puts it after that frame, and the capture
    // itself leaves this dispatcher. Nothing awaits it, and it does not throw.
    LaunchedEffect(lifecycle, periodicBackup) {
        var previous = Lifecycle.State.INITIALIZED
        lifecycle.currentStateFlow.collect { state ->
            val cameForward = state > previous && state.isAtLeast(Lifecycle.State.STARTED)
            previous = state
            if (!cameForward) return@collect

            withFrameNanos { }
            periodicBackup.captureIfDue()
        }
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
