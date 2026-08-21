package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.BudgetProgress
import kotlinx.datetime.YearMonth
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.model.CreditCardUi

/**
 * **Almost every figure on the dashboard spans accounts**, and a figure that spans
 * accounts is consolidated by nature: it is a [ConsolidatedAmount], built by the one
 * reducer in the builder, and never a number a formatting site denominates with the base
 * currency by hand (design D29). Only what belongs to a single account or facade — an
 * account's balance, a card's limit, a recurring's amount — carries a [DisplayAmount],
 * denominated by the account that originated it.
 */
sealed interface DashboardComponent {
    /** What this component is, and everything the catalog declares about it. */
    val type: DashboardComponentType

    /** How the component is named in the saved layout — the type's own key. */
    val key: String get() = type.key

    data class TotalBalance(
        val amount: ConsolidatedAmount,
    ) : DashboardComponent {
        override val type = DashboardComponentType.TOTAL_BALANCE
    }

    data class OverallBalanceStats(
        val income: ConsolidatedAmount,
        val expense: ConsolidatedAmount,
    ) : DashboardComponent {
        override val type = DashboardComponentType.OVERALL_BALANCE_STATS
    }

    data class ConcreteBalanceStats(
        val income: ConsolidatedAmount,
        val expense: ConsolidatedAmount,
    ) : DashboardComponent {
        override val type = DashboardComponentType.CONCRETE_BALANCE_STATS
    }

    data class PendingBalanceStats(
        val pendingIncome: ConsolidatedAmount,
        val pendingExpense: ConsolidatedAmount,
    ) : DashboardComponent {
        override val type = DashboardComponentType.PENDING_BALANCE_STATS
    }

    /**
     * What has not settled yet and is no longer in the future: this month's untreated
     * recurring templates, plus the invoices left to pay whose due month has arrived.
     *
     * The two sources are disjoint by construction — an untreated template has written no
     * entry, so it is in no invoice's owed — which is why nothing here deduplicates.
     */
    data class MonthSettlement(
        val incoming: ConsolidatedAmount,
        val outgoing: ConsolidatedAmount,
    ) : DashboardComponent {
        override val type = DashboardComponentType.MONTH_SETTLEMENT
    }

    data class CreditCardBalanceStats(
        val payment: ConsolidatedAmount,
        val expense: ConsolidatedAmount,
    ) : DashboardComponent {
        override val type = DashboardComponentType.CREDIT_CARD_BALANCE_STATS
    }

    data class AccountsOverview(
        val accounts: List<DashboardAccountUi>,
    ) : DashboardComponent {
        override val type = DashboardComponentType.ACCOUNTS_OVERVIEW
    }

    sealed interface CreditCardsPager : DashboardComponent {
        override val type: DashboardComponentType
            get() = DashboardComponentType.CREDIT_CARDS_PAGER

        data class Content(
            val creditCards: List<CreditCardUi>,
            // Domain invoices kept alongside the flat cards so the dashboard can open the
            // domain-taking pay/advance/edit-balance modals; aligned by index.
            val domainInvoices: List<Invoice?>,
            // The limit denominated in the card's own currency — the account behind the
            // card is the only place that states it, and the flat `CreditCardUi` does not
            // carry it. Aligned by index, like the invoices above.
            val limits: List<DisplayAmount>,
        ) : CreditCardsPager

        data object Empty : CreditCardsPager
    }

    data class SpendingByCategory(
        val categorySpending: List<CategorySpending>,
    ) : DashboardComponent {
        override val type = DashboardComponentType.SPENDING_BY_CATEGORY
    }

    data class IncomeByCategory(
        val categoryIncome: List<CategorySpending>,
    ) : DashboardComponent {
        override val type = DashboardComponentType.INCOME_BY_CATEGORY
    }

    data class Budgets(
        val budgetProgress: List<BudgetProgress>,
        /**
         * The month this progress is about — carried so the detail opened from here reads
         * the same one. Progress is a fact about a month, and the dashboard's month is
         * chosen by the user.
         */
        val targetMonth: YearMonth,
    ) : DashboardComponent {
        override val type = DashboardComponentType.BUDGETS
    }

    data class PendingRecurring(
        val recurringList: List<PendingRecurringUi>,
    ) : DashboardComponent {
        override val type = DashboardComponentType.PENDING_RECURRING
    }

    data class Recents(
        // Already mapped by the builder — this list is what the section renders, and the
        // section holds no ledger (`presentation-mapping`, design D12). No perspective:
        // the dashboard spans every account and card.
        val transactions: List<TransactionUi>,
        val hasMore: Boolean,
    ) : DashboardComponent {
        override val type = DashboardComponentType.RECENTS
    }

    data class QuickActions(
        val actions: List<NavDestination>,
    ) : DashboardComponent {
        override val type = DashboardComponentType.QUICK_ACTIONS
    }
}

fun DashboardComponent.toViewingVariant(config: Map<String, String>): DashboardComponentVariant = when (this) {
    is DashboardComponent.TotalBalance -> DashboardComponentVariant.TotalBalance.Viewing(this, config)
    is DashboardComponent.OverallBalanceStats -> DashboardComponentVariant.OverallBalanceStats.Viewing(this, config)
    is DashboardComponent.ConcreteBalanceStats -> DashboardComponentVariant.ConcreteBalanceStats.Viewing(this, config)
    is DashboardComponent.PendingBalanceStats -> DashboardComponentVariant.PendingBalanceStats.Viewing(this, config)
    is DashboardComponent.MonthSettlement -> DashboardComponentVariant.MonthSettlement.Viewing(this, config)
    is DashboardComponent.CreditCardBalanceStats -> DashboardComponentVariant.CreditCardBalanceStats.Viewing(this, config)
    is DashboardComponent.AccountsOverview -> DashboardComponentVariant.AccountsOverview.Viewing(this, config)
    is DashboardComponent.CreditCardsPager -> DashboardComponentVariant.CreditCardsPager.Viewing(this, config)
    is DashboardComponent.SpendingByCategory -> DashboardComponentVariant.SpendingByCategory.Viewing(this, config)
    is DashboardComponent.IncomeByCategory -> DashboardComponentVariant.IncomeByCategory.Viewing(this, config)
    is DashboardComponent.Budgets -> DashboardComponentVariant.Budgets.Viewing(this, config)
    is DashboardComponent.PendingRecurring -> DashboardComponentVariant.PendingRecurring.Viewing(this, config)
    is DashboardComponent.Recents -> DashboardComponentVariant.Recents.Viewing(this, config)
    is DashboardComponent.QuickActions -> DashboardComponentVariant.QuickActions.Viewing(this, config)
}
