package com.neoutils.finsight.ui.screen.exchangeRates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.exchange_rates_add
import com.neoutils.finsight.ui.component.AdaptivePane
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormModal
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The rate archive presented in the detail pane on extra-wide windows, beside the settings
 * screen that opened it — the same arrangement Support uses for a conversation.
 *
 * Pane-only: it is shown exclusively in the pane and dismissed (never demoted to a bottom
 * sheet) when the window shrinks, because below the breakpoint the archive is a route of
 * its own and pushing it is the right presentation.
 *
 * The content is [ExchangeRatesContent], unchanged; only the button that adds a rate is
 * restated here, since it belongs to the host and not to the archive.
 */
class ExchangeRatesDetail(
    /**
     * The way to the history, which is a **route** and therefore the host's to give: a
     * pane has no navigation of its own, and the archive still has to be able to reach
     * the observations behind the rate in force.
     */
    private val onOpenHistory: (currency: String?) -> Unit = {},
) : AdaptivePane() {

    @Composable
    override fun PaneContent() {
        val analytics = koinInject<Analytics>()
        val modalManager = LocalModalManager.current
        val viewModel = koinViewModel<ExchangeRatesViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            analytics.logScreenView("exchange_rates")
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ExchangeRatesContent(
                uiState = uiState,
                onOpenHistory = onOpenHistory,
                modifier = Modifier.fillMaxSize(),
            )

            FloatingActionButton(
                onClick = { modalManager.show(ExchangeRateFormModal()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.exchange_rates_add),
                )
            }
        }
    }
}
