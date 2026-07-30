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
import com.neoutils.finsight.domain.usecase.RelabelLegacyAccountCurrencyUseCase
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
    val relabelLegacyAccountCurrency = koinInject<RelabelLegacyAccountCurrencyUseCase>()

    LaunchedEffect(Unit) {
        val userId = authService.getUserId()
        analytics.setUserId(userId)
        crashlytics.setUserId(userId)

        // A one-off step, and one the schema version cannot carry because it depends on the
        // device's region. It is idempotent by the claim it writes, so running it from a
        // composition is safe; what it must not be is skipped, since a database written
        // before this change says reais while its user has been reading dollars.
        relabelLegacyAccountCurrency()
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
