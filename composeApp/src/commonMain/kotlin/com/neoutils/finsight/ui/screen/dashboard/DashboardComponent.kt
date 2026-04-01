package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.ui.model.CreditCardUi

sealed interface DashboardComponent {
    val key: String

    data class TotalBalance(
        val amount: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.TOTAL_BALANCE.value
    }

    data class ConcreteBalanceStats(
        val income: Double,
        val expense: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.CONCRETE_BALANCE_STATS.value
    }

    data class PendingBalanceStats(
        val pendingIncome: Double,
        val pendingExpense: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.PENDING_BALANCE_STATS.value
    }

    data class CreditCardBalanceStats(
        val payment: Double,
        val expense: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value
    }

    data class AccountsOverview(
        val accounts: List<DashboardAccountUi>,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.ACCOUNTS_OVERVIEW.value
    }

    sealed interface CreditCardsPager : DashboardComponent {
        override val key: String
            get() = DashboardComponentKey.CREDIT_CARDS_PAGER.value

        data class Content(
            val creditCards: List<CreditCardUi>,
        ) : CreditCardsPager

        data object Empty : CreditCardsPager
    }

    data class SpendingByCategory(
        val categorySpending: List<CategorySpending>,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.SPENDING_BY_CATEGORY.value
    }

    data class IncomeByCategory(
        val categoryIncome: List<CategorySpending>,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.INCOME_BY_CATEGORY.value
    }

    data class Budgets(
        val budgetProgress: List<BudgetProgress>,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.BUDGETS.value
    }

    data class PendingRecurring(
        val recurringList: List<Recurring>,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.PENDING_RECURRING.value
    }

    data class Recents(
        val operations: List<Operation>,
        val hasMore: Boolean,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.RECENTS.value
    }

    data class QuickActions(
        val actions: List<QuickActionType>,
    ) : DashboardComponent {
        override val key = DashboardComponentKey.QUICK_ACTIONS.value
    }
}
