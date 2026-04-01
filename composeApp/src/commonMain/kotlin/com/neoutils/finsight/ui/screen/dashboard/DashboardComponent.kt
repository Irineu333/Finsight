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

enum class DashboardComponentKey(val value: String) {
    TOTAL_BALANCE("total_balance"),
    CONCRETE_BALANCE_STATS("balance_stats_concrete"),
    PENDING_BALANCE_STATS("balance_stats_pending"),
    CREDIT_CARD_BALANCE_STATS("balance_stats_credit_card"),
    ACCOUNTS_OVERVIEW("accounts_overview"),
    CREDIT_CARDS_PAGER("credit_cards_pager"),
    SPENDING_BY_CATEGORY("spending_by_category"),
    INCOME_BY_CATEGORY("income_by_category"),
    BUDGETS("budgets"),
    PENDING_RECURRING("pending_recurring"),
    RECENTS("recents"),
    QUICK_ACTIONS("quick_actions"),
}

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
