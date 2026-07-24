package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.ui.screen.transactions.TransactionScope
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.BalanceOverview
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.ui.theme.TextLight1
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.summary_card_adjustments
import com.neoutils.finsight.resources.summary_card_card_expenses
import com.neoutils.finsight.resources.summary_card_current_balance
import com.neoutils.finsight.resources.summary_card_final_balance
import com.neoutils.finsight.resources.summary_card_final_debt
import com.neoutils.finsight.resources.summary_card_income
import com.neoutils.finsight.resources.summary_card_net
import com.neoutils.finsight.resources.summary_card_opening_balance
import com.neoutils.finsight.resources.summary_card_opening_debt
import com.neoutils.finsight.resources.summary_card_opening_net
import com.neoutils.finsight.resources.summary_card_outgoing
import com.neoutils.finsight.resources.summary_card_payments
import com.neoutils.finsight.resources.summary_card_scope_accounts
import com.neoutils.finsight.resources.summary_card_scope_all
import com.neoutils.finsight.resources.summary_card_scope_cards
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * The summary of the selected perimeter. Period and scope live *inside* the card, at
 * the top: the card's frame is what says "this governs everything below" — the chips
 * govern the card and the list, while the filters under it govern only the list.
 *
 * The chips stay anchored while the body animates, so switching scope reads as the same
 * card answering a different question rather than as a new card arriving.
 */
@Composable
fun SummaryCard(
    balanceOverview: BalanceOverview,
    selectedScope: TransactionScope,
    selectedYearMonth: YearMonth,
    onScopeSelected: (TransactionScope) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    isCurrentMonth: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The chips carry their own inset, so a flat 20 all round reads as more
                // room above than below: the top is discounted by that inset so the
                // label lands where every other line of the card starts.
                .padding(start = 20.dp, top = 20.dp - CHIP_INSET, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PeriodChip(
                    selectedYearMonth = selectedYearMonth,
                    onMonthSelected = onMonthSelected
                )

                ScopeChip(
                    selectedScope = selectedScope,
                    onScopeSelected = onScopeSelected
                )
            }

            AnimatedContent(
                targetState = balanceOverview,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { overview ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (overview) {
                        is BalanceOverview.Accounts -> AccountsBody(
                            overview = overview,
                            isCurrentMonth = isCurrentMonth,
                        )

                        is BalanceOverview.Cards -> CardsBody(overview)

                        is BalanceOverview.Overall -> OverallBody(overview)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AccountsBody(
    overview: BalanceOverview.Accounts,
    isCurrentMonth: Boolean,
) {
    SummaryRow(
        label = stringResource(Res.string.summary_card_opening_balance),
        amount = overview.openingBalance,
        color = colorScheme.onSurface,
        signDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
    )

    SummaryRow(
        label = stringResource(Res.string.summary_card_income),
        amount = overview.income,
        color = Income,
        signDisplay = SignDisplay.ALWAYS_POSITIVE
    )

    SummaryRow(
        label = stringResource(Res.string.summary_card_outgoing),
        amount = overview.expense,
        color = Expense,
        signDisplay = SignDisplay.ALWAYS_NEGATIVE
    )

    // An invoice payment leaves this perimeter, so it moves the balance — which is why
    // it is a signed flow here and merely informative in the overall scope.
    overview.invoicePayment?.let { payment ->
        SummaryRow(
            label = stringResource(Res.string.summary_card_payments),
            amount = payment,
            color = InvoicePayment,
            signDisplay = SignDisplay.ALWAYS_NEGATIVE
        )
    }

    overview.adjustment?.let { adjustment ->
        SummaryRow(
            label = stringResource(Res.string.summary_card_adjustments),
            amount = adjustment,
            color = Adjustment,
            signDisplay = SignDisplay.SHOW_ALWAYS
        )
    }

    HorizontalDivider()

    SummaryRow(
        label = stringResource(
            if (isCurrentMonth) Res.string.summary_card_current_balance
            else Res.string.summary_card_final_balance
        ),
        amount = overview.finalBalance,
        color = colorScheme.onSurface,
        config = SummaryRowConfig.Total,
        signDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
    )
}

@Composable
private fun ColumnScope.CardsBody(overview: BalanceOverview.Cards) {
    // The flows run in the ledger's own sign, like the accounts ones: spending takes the
    // balance down, a payment brings it up. Reading the card's book the other way round —
    // debt positive — would make spending read `+90`, which is not how anyone reads a
    // statement. The two ends are the exception: a line called "debt" answers *how much
    // is owed*, so it carries no sign at all (see [SignDisplay.OWED]).
    SummaryRow(
        label = stringResource(Res.string.summary_card_opening_debt),
        amount = overview.openingBalance,
        color = colorScheme.onSurface,
        signDisplay = SignDisplay.OWED
    )

    SummaryRow(
        label = stringResource(Res.string.summary_card_card_expenses),
        amount = overview.expense,
        color = Expense,
        signDisplay = SignDisplay.ALWAYS_NEGATIVE
    )

    overview.payment?.let { payment ->
        SummaryRow(
            label = stringResource(Res.string.summary_card_payments),
            amount = payment,
            color = InvoicePayment,
            signDisplay = SignDisplay.ALWAYS_POSITIVE
        )
    }

    overview.adjustment?.let { adjustment ->
        SummaryRow(
            label = stringResource(Res.string.summary_card_adjustments),
            amount = adjustment,
            color = Adjustment,
            signDisplay = SignDisplay.SHOW_ALWAYS
        )
    }

    HorizontalDivider()

    SummaryRow(
        label = stringResource(Res.string.summary_card_final_debt),
        amount = overview.finalBalance,
        color = colorScheme.onSurface,
        config = SummaryRowConfig.Total,
        signDisplay = SignDisplay.OWED
    )
}

@Composable
private fun ColumnScope.OverallBody(overview: BalanceOverview.Overall) {
    SummaryRow(
        label = stringResource(Res.string.summary_card_opening_net),
        amount = overview.openingNet,
        color = colorScheme.onSurface,
        signDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
    )

    SummaryRow(
        label = stringResource(Res.string.summary_card_income),
        amount = overview.income,
        color = Income,
        signDisplay = SignDisplay.ALWAYS_POSITIVE
    )

    SummaryRow(
        label = stringResource(Res.string.summary_card_outgoing),
        amount = overview.expense,
        color = Expense,
        signDisplay = SignDisplay.ALWAYS_NEGATIVE
    )

    // Both legs are inside this perimeter, so the payment moves nothing: shown without
    // a sign and in a quieter tone, precisely so the column above still adds up.
    overview.invoicePayment?.let { payment ->
        SummaryRow(
            label = stringResource(Res.string.summary_card_payments),
            amount = payment,
            color = InvoicePayment,
            signDisplay = SignDisplay.NONE
        )
    }

    overview.adjustment?.let { adjustment ->
        SummaryRow(
            label = stringResource(Res.string.summary_card_adjustments),
            amount = adjustment,
            color = Adjustment,
            signDisplay = SignDisplay.SHOW_ALWAYS
        )
    }

    HorizontalDivider()

    SummaryRow(
        label = stringResource(Res.string.summary_card_net),
        amount = overview.finalNet,
        color = colorScheme.onSurface,
        config = SummaryRowConfig.Total,
        signDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
    )
}

/**
 * The two chips are twins: same shape, same tone, same affordance, and the *whole*
 * chip is the tap target — a chip whose label alone reacted would look like a button
 * and behave like a word.
 */
@Composable
private fun SummaryChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit,
) = Box(modifier = modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceContainerHighest,
        contentColor = colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = CHIP_INSET, bottom = CHIP_INSET),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = chipTextStyle)

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }

    menu()
}

@Composable
private fun PeriodChip(
    selectedYearMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SummaryChip(
        label = LocalDateFormats.current.yearMonth.format(selectedYearMonth),
        onClick = { expanded = true },
    ) {
        MonthPickerDropdownMenu(
            expanded = expanded,
            selectedYearMonth = selectedYearMonth,
            onDismissRequest = { expanded = false },
            onMonthSelected = { selected ->
                onMonthSelected(selected)
                expanded = false
            },
        )
    }
}

@Composable
private fun ScopeChip(
    selectedScope: TransactionScope,
    onScopeSelected: (TransactionScope) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SummaryChip(
        label = scopeName(selectedScope),
        onClick = { expanded = true },
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TransactionScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = { Text(scopeName(scope)) },
                    onClick = {
                        onScopeSelected(scope)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** The scope's name, shared by the chip and its menu so they cannot drift. */
@Composable
private fun scopeName(scope: TransactionScope) = stringResource(
    when (scope) {
        TransactionScope.ALL -> Res.string.summary_card_scope_all
        TransactionScope.ACCOUNTS -> Res.string.summary_card_scope_accounts
        TransactionScope.CARDS -> Res.string.summary_card_scope_cards
    }
)

private val chipTextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

/**
 * The chip's own vertical breathing room. Below the row it is what separates the chips
 * from the figures, and there it is welcome; above it, it lands on top of the card's
 * padding, which is why that one is discounted by exactly this much.
 */
private val CHIP_INSET = 6.dp

@Composable
private fun SummaryRow(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    config: SummaryRowConfig = SummaryRowConfig.Default,
    signDisplay: SignDisplay = SignDisplay.SHOW_ONLY_NEGATIVE
) {
    val formatter = LocalCurrencyFormatter.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = config.labelStyle
        )

        Text(
            text = signDisplay.format(amount, formatter::format),
            style = config.amountStyle.copy(color = color)
        )
    }
}

enum class SignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,

    /** The figure carries its own sign, and a positive one is spelled out. */
    SHOW_ALWAYS,

    /** The figure carries its own sign, and only a negative one is visible. */
    SHOW_ONLY_NEGATIVE,

    /** No sign at all — an informative line that is not part of any sum. */
    NONE,

    /**
     * How much is owed, from a balance in the ledger's sign: a liability you owe on is
     * stored negative, and the line answers the magnitude of the debt. A card in credit
     * owes nothing, so it reads zero rather than a negative debt.
     */
    OWED;

    fun format(amount: Double, format: (Double) -> String): String = when (this) {
        ALWAYS_POSITIVE -> "+${format(amount)}"
        ALWAYS_NEGATIVE -> "-${format(amount)}"
        SHOW_ALWAYS -> if (amount > 0) "+${format(amount)}" else format(amount)
        SHOW_ONLY_NEGATIVE, NONE -> format(amount)
        OWED -> format(maxOf(0.0, -amount))
    }
}

data class SummaryRowConfig(
    val labelStyle: TextStyle,
    val amountStyle: TextStyle
) {
    companion object {
        val Default
            @Composable
            get() = SummaryRowConfig(
                labelStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextLight1
                ),
                amountStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )

        val Total
            @Composable
            get() = SummaryRowConfig(
                labelStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                ),
                amountStyle = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
    }
}
