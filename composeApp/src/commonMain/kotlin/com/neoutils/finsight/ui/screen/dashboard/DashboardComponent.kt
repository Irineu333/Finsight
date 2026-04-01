package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_accounts
import com.neoutils.finsight.resources.dashboard_budgets
import com.neoutils.finsight.resources.dashboard_categories
import com.neoutils.finsight.resources.dashboard_credit_cards
import com.neoutils.finsight.resources.dashboard_installments
import com.neoutils.finsight.resources.dashboard_recurring
import com.neoutils.finsight.resources.dashboard_reports
import com.neoutils.finsight.resources.dashboard_support
import com.neoutils.finsight.ui.model.CreditCardUi
import com.neoutils.finsight.util.UiText

sealed interface DashboardComponent {
    val key: String

    data class TotalBalance(
        val amount: Double,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "total_balance"
        }
    }

    data class ConcreteBalanceStats(
        val income: Double,
        val expense: Double,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "balance_stats_concrete"
        }
    }

    data class PendingBalanceStats(
        val pendingIncome: Double,
        val pendingExpense: Double,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "balance_stats_pending"
        }
    }

    data class CreditCardBalanceStats(
        val payment: Double,
        val expense: Double,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "balance_stats_credit_card"
        }
    }

    data class AccountsOverview(
        val accounts: List<DashboardAccountUi>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "accounts_overview"
        }
    }

    sealed interface CreditCardsPager : DashboardComponent {
        override val key: String
            get() = KEY

        companion object {
            const val KEY = "credit_cards_pager"
        }

        data class Content(
            val creditCards: List<CreditCardUi>,
        ) : CreditCardsPager

        data object Empty : CreditCardsPager
    }

    data class SpendingByCategory(
        val categorySpending: List<CategorySpending>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "spending_by_category"
        }
    }

    data class IncomeByCategory(
        val categoryIncome: List<CategorySpending>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "income_by_category"
        }
    }

    data class Budgets(
        val budgetProgress: List<BudgetProgress>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "budgets"
        }
    }

    data class PendingRecurring(
        val recurringList: List<Recurring>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "pending_recurring"
        }
    }

    data class Recents(
        val operations: List<Operation>,
        val hasMore: Boolean,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "recents"
        }
    }

    data class QuickActions(
        val actions: List<QuickActionType>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "quick_actions"
        }
    }
}

fun DashboardComponent.toViewingVariant(
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    onOpenQuickAction: (QuickActionType) -> Unit,
): DashboardComponentVariant = when (this) {
    is DashboardComponent.TotalBalance -> DashboardComponentVariant.TotalBalance.Viewing(component = this)
    is DashboardComponent.ConcreteBalanceStats -> DashboardComponentVariant.ConcreteBalanceStats.Viewing(
        component = this,
        openTransactions = openTransactions,
    )

    is DashboardComponent.PendingBalanceStats -> DashboardComponentVariant.PendingBalanceStats.Viewing(component = this)
    is DashboardComponent.CreditCardBalanceStats -> DashboardComponentVariant.CreditCardBalanceStats.Viewing(component = this)
    is DashboardComponent.AccountsOverview -> DashboardComponentVariant.AccountsOverview.Viewing(
        component = this,
        onOpenQuickAction = onOpenQuickAction,
    )

    is DashboardComponent.CreditCardsPager -> DashboardComponentVariant.CreditCardsPager.Viewing(
        component = this,
        onOpenQuickAction = onOpenQuickAction,
    )

    is DashboardComponent.SpendingByCategory -> DashboardComponentVariant.SpendingByCategory.Viewing(component = this)
    is DashboardComponent.IncomeByCategory -> DashboardComponentVariant.IncomeByCategory.Viewing(component = this)
    is DashboardComponent.Budgets -> DashboardComponentVariant.Budgets.Viewing(component = this)
    is DashboardComponent.PendingRecurring -> DashboardComponentVariant.PendingRecurring.Viewing(
        component = this,
        onOpenQuickAction = onOpenQuickAction,
    )

    is DashboardComponent.Recents -> DashboardComponentVariant.Recents.Viewing(
        component = this,
        openTransactions = openTransactions,
    )

    is DashboardComponent.QuickActions -> DashboardComponentVariant.QuickActions.Viewing(
        component = this,
        onOpenQuickAction = onOpenQuickAction,
    )
}

enum class QuickActionType(val title: UiText) {
    BUDGETS(title = UiText.Res(Res.string.dashboard_budgets)),
    CATEGORIES(title = UiText.Res(Res.string.dashboard_categories)),
    CREDIT_CARDS(title = UiText.Res(Res.string.dashboard_credit_cards)),
    ACCOUNTS(title = UiText.Res(Res.string.dashboard_accounts)),
    RECURRING(title = UiText.Res(Res.string.dashboard_recurring)),
    REPORTS(title = UiText.Res(Res.string.dashboard_reports)),
    INSTALLMENTS(title = UiText.Res(Res.string.dashboard_installments)),
    SUPPORT(title = UiText.Res(Res.string.dashboard_support)),
}
