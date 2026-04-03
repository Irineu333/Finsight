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

fun DashboardComponent.toViewingVariant(config: Map<String, String>): DashboardComponentVariant = when (this) {
    is DashboardComponent.TotalBalance -> DashboardComponentVariant.TotalBalance.Viewing(this, config)
    is DashboardComponent.ConcreteBalanceStats -> DashboardComponentVariant.ConcreteBalanceStats.Viewing(this, config)
    is DashboardComponent.PendingBalanceStats -> DashboardComponentVariant.PendingBalanceStats.Viewing(this, config)
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
