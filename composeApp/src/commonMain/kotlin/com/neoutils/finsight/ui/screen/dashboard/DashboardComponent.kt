package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
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
        override val key: String = "total_balance",
    ) : DashboardComponent

    data class ConcreteBalanceStats(
        val income: Double,
        val expense: Double,
        override val key: String = "balance_stats_concrete",
    ) : DashboardComponent

    data class PendingBalanceStats(
        val pendingIncome: Double,
        val pendingExpense: Double,
        override val key: String = "balance_stats_pending",
    ) : DashboardComponent

    data class AccountsOverview(
        val accounts: List<DashboardAccountUi>,
        override val key: String = "accounts_overview",
    ) : DashboardComponent

    data class CreditCardsPager(
        val creditCards: List<CreditCardUi>,
        override val key: String = "credit_cards_pager",
    ) : DashboardComponent

    data class SpendingPager(
        val categorySpending: List<CategorySpending>,
        val budgetProgress: List<BudgetProgress>,
        override val key: String = "spending_pager",
    ) : DashboardComponent

    data class PendingRecurring(
        val recurringList: List<Recurring>,
        override val key: String = "pending_recurring",
    ) : DashboardComponent

    data class Recents(
        val operations: List<Operation>,
        val hasMore: Boolean,
        override val key: String = "recents",
    ) : DashboardComponent

    data class QuickActions(
        val actions: List<QuickActionType>,
        override val key: String = "quick_actions_",
    ) : DashboardComponent
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
