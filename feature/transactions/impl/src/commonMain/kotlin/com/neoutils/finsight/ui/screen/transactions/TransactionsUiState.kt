@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val currentMonth
    get() = Clock.System.now().toYearMonth()

data class TransactionsUiState(
    val transactions: Map<LocalDate, List<Transaction>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview.Overall(),
    val selectedScope: TransactionScope = TransactionScope.ALL,
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
    val selectedCategory: Category? = null,
    val categories: List<Category> = listOf(),
    val selectedLabel: TransactionLabel? = null,
    val selectedTarget: TransactionTarget? = null,
    val showRecurringOnly: Boolean = false,
    val showInstallmentOnly: Boolean = false,
    val facadeLookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
) {

    val isCurrentMonth = selectedYearMonth == currentMonth
    val isFutureMonth = selectedYearMonth > currentMonth

    /**
     * Whether the target chip still has work to do. In the scoped modes it would be
     * the same decision in a second control, able to contradict the first.
     */
    val mustShowTargetFilter = selectedScope == TransactionScope.ALL

    /** An instalment is a card arrangement, so the filter has nothing to narrow without one. */
    val mustShowInstallmentFilter = selectedScope != TransactionScope.ACCOUNTS

    /**
     * The summary of one scope — opening, flows, closing — in three shapes rather than
     * one shape with fields that only apply in one mode. Which lines exist is decided
     * here, by the mapper; a `null` flow is a line the month does not have, so the card
     * never has to ask whether a number is worth showing.
     */
    sealed interface BalanceOverview {

        /**
         * The `ASSET` perimeter. An invoice payment *is* a flow here — its liability
         * leg lies outside the perimeter — which is why [invoicePayment] exists and a
         * transfer between accounts does not appear at all.
         *
         * `finalBalance = openingBalance + income − expense − invoicePayment + adjustment`
         */
        data class Accounts(
            val openingBalance: Double = 0.0,
            val income: Double = 0.0,
            val expense: Double = 0.0,
            val invoicePayment: Double? = null,
            val adjustment: Double? = null,
            val finalBalance: Double = 0.0,
        ) : BalanceOverview

        /**
         * The `LIABILITY` perimeter, in the ledger's own sign — a card you owe on has a
         * negative balance, spending takes it down and a payment brings it up, exactly
         * as in [Accounts]. Presenting it as positive debt would invert every flow and
         * make spending read `+90`.
         *
         * `finalBalance = openingBalance − expense + payment + adjustment`
         */
        data class Cards(
            val openingBalance: Double = 0.0,
            val expense: Double = 0.0,
            val payment: Double? = null,
            val adjustment: Double? = null,
            val finalBalance: Double = 0.0,
        ) : BalanceOverview

        /**
         * Both perimeters at once. [expense] aggregates account and card spending — the
         * two sets are disjoint, since a card purchase has no `ASSET` leg — and
         * [invoicePayment] is *informative*: both of its legs are inside the perimeter,
         * so it sums to zero here and must be shown without a sign, outside the total.
         *
         * `finalNet = openingNet + income − expense + adjustment`
         */
        data class Overall(
            val openingNet: Double = 0.0,
            val income: Double = 0.0,
            val expense: Double = 0.0,
            val invoicePayment: Double? = null,
            val adjustment: Double? = null,
            val finalNet: Double = 0.0,
        ) : BalanceOverview
    }
}
