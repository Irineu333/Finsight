@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.viewCreditCard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_balance
import com.neoutils.finsight.resources.credit_card_form_limit_label
import com.neoutils.finsight.resources.credit_card_ui_closes_on
import com.neoutils.finsight.resources.credit_card_ui_day
import com.neoutils.finsight.resources.credit_card_ui_due_on
import com.neoutils.finsight.resources.credit_cards_unarchive
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewCreditCardModal(
    private val cardId: Long,
) : AdaptiveModal() {

    // Both slots resolve the same ViewModel and collect the same state under this
    // modal as their ViewModelStoreOwner — see ViewCategoryModal.
    @Composable
    private fun rememberViewState(): Pair<ViewCreditCardViewModel, ViewCreditCardUiState> {
        val viewModel = koinViewModel<ViewCreditCardViewModel> { parametersOf(cardId) }
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
                    is ViewCreditCardEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewCreditCardUiState.Loading -> DetailLoadingState()
            ViewCreditCardUiState.Error -> DetailErrorState()
            is ViewCreditCardUiState.Content -> ContentBody(state)
        }
    }

    @Composable
    private fun ContentBody(uiState: ViewCreditCardUiState.Content) {
        val formatter = LocalCurrencyFormatter.current
        val card = uiState.creditCard

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = AppIcon.fromKey(card.iconKey).icon,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Text(
                    text = card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            DetailRow(
                label = stringResource(Res.string.credit_card_form_limit_label),
                value = formatter.format(card.limit),
            )

            Spacer(Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.credit_card_ui_closes_on),
                value = stringResource(Res.string.credit_card_ui_day, card.closingDay),
            )

            Spacer(Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.credit_card_ui_due_on),
                value = stringResource(Res.string.credit_card_ui_day, card.dueDay),
            )

            Spacer(Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.credit_card_balance),
                value = formatter.format(uiState.balance),
            )
        }
    }

    @Composable
    override fun DetailActions() {
        val (viewModel, uiState) = rememberViewState()
        val content = uiState as? ViewCreditCardUiState.Content ?: return

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // This detail is reached only from the archived list, so the only offer is
            // unarchiving — retiring an active card stays in the pager's CardActions
            // (design D6). The when leaves room for the non-archived branch without
            // implementing it.
            when (content.creditCard.isArchived) {
                true -> OutlinedActionButton(
                    label = stringResource(Res.string.credit_cards_unarchive),
                    icon = Icons.Default.Unarchive,
                    contentColor = colorScheme.primary,
                    onClick = { viewModel.onAction(ViewCreditCardAction.Unarchive) },
                    modifier = Modifier.weight(1f),
                )

                false -> Unit
            }
        }
    }

    @Composable
    private fun DetailRow(
        label: String,
        value: String,
        valueColor: Color = colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor
            )
        }
    }
}
