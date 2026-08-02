package com.neoutils.finsight.ui.modal.viewCurrency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currencies_archived_label
import com.neoutils.finsight.resources.view_currency_accounts
import com.neoutils.finsight.resources.view_currency_base_label
import com.neoutils.finsight.resources.view_currency_budgets
import com.neoutils.finsight.resources.view_currency_edit
import com.neoutils.finsight.resources.view_currency_rates
import com.neoutils.finsight.resources.view_currency_unarchive
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.CurrencyGlyph
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.ui.modal.archiveCurrency.ArchiveCurrencyModal
import com.neoutils.finsight.ui.modal.currencyForm.CurrencyFormModal
import com.neoutils.finsight.ui.modal.deleteCurrency.DeleteCurrencyModal
import com.neoutils.finsight.ui.theme.Info
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One currency, and what may be done to it — the intermediate step between the list and
 * an action.
 *
 * The list used to carry the actions inline, and this replaces that: a vertical list of
 * rows with buttons in them makes every row a small toolbar, and puts a destructive
 * action one mis-tap from a scroll. It is also what the rest of the app does — an
 * account, a card and a category are all opened before they are acted on.
 *
 * What it shows is what the actions depend on: **how many accounts and budgets denominate
 * the currency** is exactly what refuses a deletion, so the screen states it instead of
 * letting the user discover it by being refused. The rule itself has one owner in
 * `DeleteCurrencyUseCase`; this reads its answer.
 */
class ViewCurrencyModal(
    private val code: String,
) : AdaptiveModal() {

    // Both slots render under this modal as their ViewModelStoreOwner, so this resolves
    // the same ViewModel and collects the same state — once, rather than copied into
    // each slot.
    @Composable
    private fun rememberViewState(): Pair<ViewCurrencyViewModel, ViewCurrencyUiState> {
        val viewModel = koinViewModel<ViewCurrencyViewModel> { parametersOf(code) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        return viewModel to uiState
    }

    @Composable
    override fun DetailContent() {
        val detailController = LocalDetailPaneController.current
        val (viewModel, uiState) = rememberViewState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    ViewCurrencyEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewCurrencyUiState.Loading -> DetailLoadingState()
            ViewCurrencyUiState.Error -> DetailErrorState()
            is ViewCurrencyUiState.Content -> ContentBody(state)
        }
    }

    @Composable
    private fun ContentBody(uiState: ViewCurrencyUiState.Content) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyGlyph(symbol = uiState.currency.symbol)

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = uiState.currency.code,
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant,
                        )

                        // A word and not only a colour, exactly as on a category card:
                        // whoever does not read colour still reads the state.
                        if (uiState.isArchived) {
                            StateBadge(stringResource(Res.string.currencies_archived_label))
                        }

                        if (uiState.isBase) {
                            StateBadge(stringResource(Res.string.view_currency_base_label))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        // The row's own name when it stores one, the platform's when it
                        // does not, and the code as the worst case.
                        text = uiState.currency.name ?: uiState.currency.code,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // What denominates it — which is what decides whether the action below
            // deletes or archives. Stated, rather than discovered through a refusal.
            DetailRow(
                label = stringResource(Res.string.view_currency_accounts),
                value = uiState.usage.accounts.toString(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_currency_budgets),
                value = uiState.usage.budgets.toString(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // A rate does not block the deletion — it goes with it — so this number is
            // what the confirmation will say out loud.
            DetailRow(
                label = stringResource(Res.string.view_currency_rates),
                value = uiState.usage.rates.toString(),
            )
        }
    }

    @Composable
    override fun DetailActions() {
        val manager = LocalModalManager.current
        val (viewModel, uiState) = rememberViewState()
        val content = uiState as? ViewCurrencyUiState.Content ?: return

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                content.isArchived -> OutlinedActionButton(
                    label = stringResource(Res.string.view_currency_unarchive),
                    icon = Icons.Default.Unarchive,
                    contentColor = colorScheme.primary,
                    onClick = { viewModel.onAction(ViewCurrencyAction.Unarchive) },
                    modifier = Modifier.weight(1f),
                )

                // The base is the one row with no retire action at all: archiving it is
                // refused, and deleting it is refused by the account it denominates. An
                // action that is always refused is worse than one not offered.
                content.isBase -> Unit

                else -> OutlinedActionButton(
                    label = stringResource(content.retireAction.label),
                    icon = content.retireAction.icon,
                    contentColor = colorScheme.error,
                    onClick = {
                        manager.show(
                            when (content.retireAction) {
                                RetireAction.DELETE -> DeleteCurrencyModal(
                                    code = content.currency.code,
                                    label = content.currency.name ?: content.currency.code,
                                    ratesToRemove = content.usage.rates,
                                )

                                RetireAction.ARCHIVE -> ArchiveCurrencyModal(
                                    code = content.currency.code,
                                    label = content.currency.name ?: content.currency.code,
                                )
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedActionButton(
                label = stringResource(Res.string.view_currency_edit),
                icon = Icons.Default.Edit,
                contentColor = Info,
                onClick = { manager.show(CurrencyFormModal(content.currency)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    @Composable
    private fun StateBadge(text: String) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    @Composable
    private fun DetailRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
