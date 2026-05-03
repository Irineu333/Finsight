package com.neoutils.finsight.feature.dashboard.screen

import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.feature.categories.model.CategorySpending
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.feature.creditCards.model.CreditCardUi

sealed interface DashboardComponent {
    val key: String

    data class TotalBalance(
        val amount: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentType.TOTAL_BALANCE.key
    }

    data class ConcreteBalanceStats(
        val income: Double,
        val expense: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentType.CONCRETE_BALANCE_STATS.key
    }

    data class PendingBalanceStats(
        val pendingIncome: Double,
        val pendingExpense: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentType.PENDING_BALANCE_STATS.key
    }

    data class CreditCardBalanceStats(
        val payment: Double,
        val expense: Double,
    ) : DashboardComponent {
        override val key = DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key
    }

    data class AccountsOverview(
        val accounts: List<DashboardAccountUi>,
    ) : DashboardComponent {
        override val key = DashboardComponentType.ACCOUNTS_OVERVIEW.key
    }

    sealed interface CreditCardsPager : DashboardComponent {
        override val key: String
            get() = DashboardComponentType.CREDIT_CARDS_PAGER.key

        data class Content(
            val creditCards: List<CreditCardUi>,
        ) : CreditCardsPager

        data object Empty : CreditCardsPager
    }

    data class SpendingByCategory(
        val categorySpending: List<CategorySpending>,
    ) : DashboardComponent {
        override val key = DashboardComponentType.SPENDING_BY_CATEGORY.key
    }

    data class IncomeByCategory(
        val categoryIncome: List<CategorySpending>,
    ) : DashboardComponent {
        override val key = DashboardComponentType.INCOME_BY_CATEGORY.key
    }

    data class Budgets(
        val budgetProgress: List<BudgetProgress>,
    ) : DashboardComponent {
        override val key = DashboardComponentType.BUDGETS.key
    }

    data class PendingRecurring(
        val recurringList: List<Recurring>,
    ) : DashboardComponent {
        override val key = DashboardComponentType.PENDING_RECURRING.key
    }

    data class Recents(
        val operations: List<Operation>,
        val hasMore: Boolean,
    ) : DashboardComponent {
        override val key = DashboardComponentType.RECENTS.key
    }

    data class QuickActions(
        val actions: List<QuickActionType>,
    ) : DashboardComponent {
        override val key = DashboardComponentType.QUICK_ACTIONS.key
    }
}

