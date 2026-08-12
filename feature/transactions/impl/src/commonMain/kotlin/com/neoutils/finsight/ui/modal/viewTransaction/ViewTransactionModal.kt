@file:OptIn(ExperimentalMaterial3Api::class, FormatStringsInDatetimeFormats::class, ExperimentalUuidApi::class)

package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencySymbols
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.DetailRow
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.TransactionLegCard
import com.neoutils.finsight.ui.component.TransactionLegConnector
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionModal
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionModal
import com.neoutils.finsight.ui.theme.*
import com.neoutils.finsight.util.RATE_SCALE
import com.neoutils.finsight.util.dayMonthYear
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewTransactionModal(
    private val transactionId: Long,
) : AdaptiveModal() {

    @Composable
    override fun DetailContent() {

        val formatter = LocalCurrencyFormatter.current
        val viewModel = koinViewModel<ViewTransactionViewModel> {
            parametersOf(transactionId)
        }

        val uiState by viewModel.uiState.collectAsState()

        val detailController = LocalDetailPaneController.current
        val recurringEntry = koinInject<RecurringEntry>()
        val navController = LocalNavController.current

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewTransactionEvent.Dismiss -> detailController.dismiss()
                    is ViewTransactionEvent.OpenRecurring -> detailController.show(
                        recurringEntry.viewRecurringModal(event.recurring.id)
                    )
                }
            }
        }

        when (val state = uiState) {
            ViewTransactionUiState.Loading -> DetailLoadingState()
            ViewTransactionUiState.Error -> DetailErrorState()
            is ViewTransactionUiState.Content -> ContentBody(
                uiState = state,
                formatter = formatter,
                detailController = detailController,
                navController = navController,
                viewModel = viewModel,
            )
        }
    }

    @Composable
    private fun ContentBody(
        uiState: ViewTransactionUiState.Content,
        formatter: com.neoutils.finsight.extension.CurrencyFormatter,
        detailController: com.neoutils.finsight.ui.component.DetailPaneController,
        navController: androidx.navigation.NavController,
        viewModel: ViewTransactionViewModel,
    ) {
        // One card per monetary leg. The route a card opens is this feature's to name;
        // whether there is one to open at all was decided by the mapper, which offers
        // none for an archived facade.
        val legs = uiState.legs { target ->
            detailController.dismiss()
            if (target.isLiability) {
                uiState.creditCard?.let { navController.navigate(CreditCardsRoute(it.id)) }
            } else {
                navController.navigate(AccountsRoute(target.accountId))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    // The transaction keeps its own colour; only the category icon reads
                    // muted when the category is archived, as elsewhere (`displayColor`).
                    val iconColor = if (uiState.category?.isArchived == true) {
                        colorScheme.onSurfaceVariant
                    } else {
                        uiState.label.color()
                    }
                    Surface(
                        color = iconColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(52.dp),
                    ) {
                        uiState.category?.let { category ->
                            Icon(
                                painter = category.icon(),
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(13.dp)
                            )
                        } ?: run {
                            Icon(
                                imageVector = uiState.label.icon(),
                                contentDescription = null,
                                tint = uiState.label.color(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(13.dp)
                            )
                        }
                    }

                    if (uiState.isCardTarget) {
                        Surface(
                            color = colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(20.dp)
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

                Spacer(Modifier.width(16.dp))

                Column {
                    // The nature, and never the direction of a leg: this surface reads
                    // no leg, so a direction here would be a property of an arbitrary
                    // choice rather than of the transaction.
                    Text(
                        text = stringResource(uiState.label.natureLabel()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = uiState.label.color()
                    )

                    // A transfer and a payment ordinarily carry neither title nor
                    // category, and are named by what they *are* — which is a fact of
                    // their form, not a reserve literal standing in for an absence.
                    // The name says what the nature above it does not: the two lines
                    // are read together, so "transferência / entre contas" is the whole
                    // sentence and repeating the first word in the second would waste
                    // the line.
                    //
                    // An expense, an income or an adjustment with neither has no such
                    // name, and the line is omitted rather than invented.
                    val fallbackTitle = when (uiState.label) {
                        TransactionLabel.PAYMENT -> stringResource(Res.string.transaction_card_payment)
                        TransactionLabel.TRANSFER -> stringResource(Res.string.view_transaction_title_transfer)
                        else -> null
                    }

                    (uiState.displayTitle ?: fallbackTitle)?.let { title ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            legs.forEachIndexed { index, leg ->
                if (index > 0) {
                    // Always between two cards: the arrow states that these are the
                    // two ends of one movement, which is as true of a transfer in one
                    // currency as of one that crossed two.
                    //
                    // The rate is the part only a cross-currency operation has, and it
                    // is a relation between the two legs, so it is drawn where it is
                    // one. The grammar is the write form's (`CounterpartAmountField`)
                    // — one unit of the source priced in the target, never either of
                    // them priced in the base — so the rate read afterwards is the one
                    // that was shown while typing.
                    TransactionLegConnector(
                        rate = uiState.appliedRate?.let { applied ->
                            stringResource(
                                Res.string.exchange_rates_quote,
                                LocalCurrencySymbols.current(applied.sourceCurrency),
                                // As many places as the rate needs, not the currency's
                                // own two: a quotient like `0,000691` reads `R$ 0,00`
                                // at two places, which is a rate of zero — a different
                                // statement from a rounded one.
                                formatter.format(
                                    amount = applied.rate,
                                    currency = applied.targetCurrency,
                                    minFractionDigits = 2,
                                    maxFractionDigits = RATE_SCALE,
                                ),
                            )
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                TransactionLegCard(
                    leg = leg,
                    // The first card is the primary leg, which in every single-currency
                    // operation is the figure the amount row used to state.
                    valueTestTag = if (index == 0) {
                        "view_transaction_amount"
                    } else {
                        "view_transaction_secondary_amount"
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // What is left as context is what belongs to the transaction and to no leg
            // of it. Everything a leg owns now lives in that leg's card.
            //
            // The category qualifies: it is the dimension of the nominal leg, which
            // carries no money and therefore produces no card. Absent when there is
            // none — "uncategorized" is the absence of a dimension, not a bucket, so
            // there is nothing to name. An archived one reads muted, keeping its place
            // in the history it labelled, as its icon does above.
            uiState.category?.let { category ->
                DetailRow(
                    label = stringResource(Res.string.view_transaction_category_label),
                    value = category.name,
                    valueColor = if (category.isArchived) {
                        colorScheme.onSurfaceVariant
                    } else {
                        colorScheme.onSurface
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            DetailRow(
                label = stringResource(Res.string.view_transaction_date_label),
                value = dayMonthYear.format(uiState.date)
            )

            uiState.recurring?.let { recurring ->
                DetailRow(
                    label = stringResource(Res.string.view_transaction_recurring_label),
                    value = recurring.label,
                    valueColor = colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        viewModel.onAction(
                            ViewTransactionAction.OpenRecurring(recurring.instance)
                        )
                    }
                )
            }
        }
    }

    @Composable
    override fun DetailActions() {
        val viewModel = koinViewModel<ViewTransactionViewModel> {
            parametersOf(transactionId)
        }
        val uiState by viewModel.uiState.collectAsState()

        val content = uiState as? ViewTransactionUiState.Content ?: return

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            val lockedMessage = when {
                // A closed invoice locks both actions; its message wins because it is
                // the more specific reason (the transaction may also sit on an
                // archived card, but the invoice is what the user acts on).
                content.invoice?.status?.isEditable == false ->
                    Res.string.view_transaction_closed_invoice_message
                // Frozen by an archived account or card: both edit and delete are
                // hidden, so without this the actions area would be blank.
                !content.isChangeable ->
                    Res.string.view_transaction_archived_message
                else -> null
            }

            if (lockedMessage != null) {
                Text(
                    text = stringResource(lockedMessage),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                EditAndDelete(content)
            }
        }
    }

    @Composable
    private fun EditAndDelete(
        uiState: ViewTransactionUiState.Content,
    ) = Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        val manager = LocalModalManager.current

        // Deleting is hidden, not disabled, when it would strand a balance on an
        // archived account: the same reason the invoice branch above hides both.
        if (uiState.isRemovable) {
            OutlinedButton(
                onClick = {
                    manager.show(DeleteTransactionModal(uiState.transaction))
                },
                modifier = (if (uiState.isEditable) Modifier.weight(1f) else Modifier.fillMaxWidth())
                    .testTag("view_transaction_delete"),
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
                    text = stringResource(Res.string.view_transaction_delete),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (uiState.isEditable) {
                OutlinedButton(
                    onClick = {
                        manager.show(EditTransactionModal(uiState.transaction))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("view_transaction_edit"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Info,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Info,
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.view_transaction_edit),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
}

// Colour, icon and label are total functions of the nature — five values, no branch
// on a leg's direction, so a surface that reads no leg has nothing left to choose.

private fun TransactionLabel.color(): Color = when (this) {
    TransactionLabel.PAYMENT -> InvoicePayment
    TransactionLabel.TRANSFER -> Info
    TransactionLabel.INCOME -> Income
    TransactionLabel.EXPENSE -> Expense
    TransactionLabel.ADJUSTMENT -> Adjustment
}

private fun TransactionLabel.icon(): ImageVector = when (this) {
    TransactionLabel.PAYMENT -> Icons.Default.Payment
    TransactionLabel.TRANSFER -> Icons.Default.SwapHoriz
    TransactionLabel.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
    TransactionLabel.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
    TransactionLabel.ADJUSTMENT -> Icons.Default.Tune
}

private fun TransactionLabel.natureLabel() = when (this) {
    TransactionLabel.PAYMENT -> Res.string.view_transaction_nature_payment
    TransactionLabel.TRANSFER -> Res.string.view_transaction_nature_transfer
    TransactionLabel.INCOME -> Res.string.view_transaction_nature_income
    TransactionLabel.EXPENSE -> Res.string.view_transaction_nature_expense
    TransactionLabel.ADJUSTMENT -> Res.string.view_transaction_nature_adjustment
}
