@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class)

package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.DetailPaneController
import com.neoutils.finsight.ui.component.DetailRow
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.TransactionLegCard
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewAdjustmentModal(
    private val transactionId: Long,
) : AdaptiveModal() {

    @Composable
    override fun DetailContent() {

        val detailController = LocalDetailPaneController.current
        val navController = LocalNavController.current
        val viewModel = koinViewModel<ViewAdjustmentViewModel> { parametersOf(transactionId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewAdjustmentEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (val state = uiState) {
            ViewAdjustmentUiState.Loading -> DetailLoadingState()
            ViewAdjustmentUiState.Error -> DetailErrorState()
            is ViewAdjustmentUiState.Content -> ContentBody(
                uiState = state,
                detailController = detailController,
                navController = navController,
            )
        }
    }

    @Composable
    private fun ContentBody(
        uiState: ViewAdjustmentUiState.Content,
        detailController: DetailPaneController,
        navController: NavController,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdjustmentIconBox(
                    showCreditCardBadge = uiState.isCardTarget
                )

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = stringResource(Res.string.view_adjustment_type_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Adjustment
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val balanceAdjust = stringResource(Res.string.view_adjustment_balance_adjust)
                    val invoiceAdjust = stringResource(Res.string.view_adjustment_invoice_adjust)
                    Text(
                        text = uiState.title ?: if (uiState.isCardTarget) invoiceAdjust else balanceAdjust,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The same leg card the transaction detail is composed of: what differs is
            // what the ledger makes differ — the verb and the explicit sign — and the
            // invoice of a card adjustment sits inside the liability card, as it does
            // for any other liability leg.
            uiState.legs { target ->
                detailController.dismiss()
                if (target.isLiability) {
                    uiState.creditCard?.let { navController.navigate(CreditCardsRoute(it.id)) }
                } else {
                    navController.navigate(AccountsRoute(target.accountId))
                }
            }.forEach { leg ->
                TransactionLegCard(
                    leg = leg,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                label = stringResource(Res.string.view_adjustment_date_label),
                value = dayMonthYear.format(uiState.date)
            )
        }
    }

    @Composable
    override fun DetailActions() {
        val manager = LocalModalManager.current
        val viewModel = koinViewModel<ViewAdjustmentViewModel> { parametersOf(transactionId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        val content = uiState as? ViewAdjustmentUiState.Content ?: return

        // Frozen by an archived account or card: say why, the same footer the
        // transaction modal shows, instead of leaving the area blank.
        if (!content.isChangeable) {
            Text(
                text = stringResource(Res.string.view_transaction_archived_message),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
            )
            return
        }
        if (!content.isDeletable) return

        OutlinedButton(
            onClick = {
                manager.show(DeleteTransactionModal(content.transaction))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colorScheme.error,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = colorScheme.error,
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(Res.string.view_adjustment_delete_label),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    private fun AdjustmentIconBox(
        showCreditCardBadge: Boolean
    ) {
        Box {
            Surface(
                color = Adjustment.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Adjustment,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (showCreditCardBadge) {
                Surface(
                    color = colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(3.dp)
                    )
                }
            }
        }
    }
}
