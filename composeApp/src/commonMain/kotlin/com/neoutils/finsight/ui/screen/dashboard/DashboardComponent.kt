package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CreditCard
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
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate

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

    data class SpendingPager(
        val categorySpending: List<CategorySpending>,
        val budgetProgress: List<BudgetProgress>,
        override val key: String = KEY,
    ) : DashboardComponent {
        companion object {
            const val KEY = "spending_pager"
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

sealed interface DashboardComponentVariant {
    val component: DashboardComponent
    val key: String get() = component.key

    sealed interface TotalBalance : DashboardComponentVariant {
        override val component: DashboardComponent.TotalBalance

        data class Viewing(
            override val component: DashboardComponent.TotalBalance,
        ) : TotalBalance

        data class Preview(
            override val component: DashboardComponent.TotalBalance =
                DashboardComponent.TotalBalance(amount = 5432.10),
        ) : TotalBalance
    }

    sealed interface ConcreteBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.ConcreteBalanceStats

        data class Viewing(
            override val component: DashboardComponent.ConcreteBalanceStats,
            val openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
        ) : ConcreteBalanceStats

        data class Preview(
            override val component: DashboardComponent.ConcreteBalanceStats = DashboardComponentPreviewFactory.concreteBalanceStats,
        ) : ConcreteBalanceStats
    }

    sealed interface PendingBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.PendingBalanceStats

        data class Viewing(
            override val component: DashboardComponent.PendingBalanceStats,
        ) : PendingBalanceStats

        data class Preview(
            override val component: DashboardComponent.PendingBalanceStats = DashboardComponentPreviewFactory.pendingBalanceStats,
        ) : PendingBalanceStats
    }

    sealed interface AccountsOverview : DashboardComponentVariant {
        override val component: DashboardComponent.AccountsOverview

        data class Viewing(
            override val component: DashboardComponent.AccountsOverview,
            val onOpenQuickAction: (QuickActionType) -> Unit,
        ) : AccountsOverview

        data class Preview(
            override val component: DashboardComponent.AccountsOverview = DashboardComponentPreviewFactory.accountsOverview,
        ) : AccountsOverview
    }

    sealed interface CreditCardsPager : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardsPager

        data class Viewing(
            override val component: DashboardComponent.CreditCardsPager,
            val onOpenQuickAction: (QuickActionType) -> Unit,
        ) : CreditCardsPager

        data class Preview(
            override val component: DashboardComponent.CreditCardsPager = DashboardComponentPreviewFactory.creditCardsPager,
        ) : CreditCardsPager
    }

    sealed interface SpendingPager : DashboardComponentVariant {
        override val component: DashboardComponent.SpendingPager

        data class Viewing(
            override val component: DashboardComponent.SpendingPager,
        ) : SpendingPager

        data class Preview(
            override val component: DashboardComponent.SpendingPager = DashboardComponentPreviewFactory.spendingPager,
        ) : SpendingPager
    }

    sealed interface PendingRecurring : DashboardComponentVariant {
        override val component: DashboardComponent.PendingRecurring

        data class Viewing(
            override val component: DashboardComponent.PendingRecurring,
            val onOpenQuickAction: (QuickActionType) -> Unit,
        ) : PendingRecurring

        data class Preview(
            override val component: DashboardComponent.PendingRecurring = DashboardComponentPreviewFactory.pendingRecurring,
        ) : PendingRecurring
    }

    sealed interface Recents : DashboardComponentVariant {
        override val component: DashboardComponent.Recents

        data class Viewing(
            override val component: DashboardComponent.Recents,
            val openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
        ) : Recents

        data class Preview(
            override val component: DashboardComponent.Recents = DashboardComponentPreviewFactory.recents,
        ) : Recents
    }

    sealed interface QuickActions : DashboardComponentVariant {
        override val component: DashboardComponent.QuickActions

        data class Viewing(
            override val component: DashboardComponent.QuickActions,
            val onOpenQuickAction: (QuickActionType) -> Unit,
        ) : QuickActions

        data class Preview(
            override val component: DashboardComponent.QuickActions = DashboardComponentPreviewFactory.quickActions,
        ) : QuickActions
    }

    companion object {
        fun previewForKey(key: String): DashboardComponentVariant? = when (key) {
            DashboardComponent.TotalBalance.KEY -> TotalBalance.Preview()
            DashboardComponent.ConcreteBalanceStats.KEY -> ConcreteBalanceStats.Preview()
            DashboardComponent.PendingBalanceStats.KEY -> PendingBalanceStats.Preview()
            DashboardComponent.AccountsOverview.KEY -> AccountsOverview.Preview()
            DashboardComponent.CreditCardsPager.KEY -> CreditCardsPager.Preview()
            DashboardComponent.SpendingPager.KEY -> SpendingPager.Preview()
            DashboardComponent.PendingRecurring.KEY -> PendingRecurring.Preview()
            DashboardComponent.Recents.KEY -> Recents.Preview()
            DashboardComponent.QuickActions.KEY -> QuickActions.Preview()
            else -> null
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
    is DashboardComponent.AccountsOverview -> DashboardComponentVariant.AccountsOverview.Viewing(
        component = this,
        onOpenQuickAction = onOpenQuickAction,
    )

    is DashboardComponent.CreditCardsPager -> DashboardComponentVariant.CreditCardsPager.Viewing(
        component = this,
        onOpenQuickAction = onOpenQuickAction,
    )

    is DashboardComponent.SpendingPager -> DashboardComponentVariant.SpendingPager.Viewing(component = this)
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

private object DashboardComponentPreviewFactory {
    private val mockAccount = Account(
        id = 1,
        name = "Conta Principal",
        iconKey = "wallet",
        isDefault = true,
        createdAt = 0,
    )

    private val mockExpenseCategory = Category(
        id = 1,
        name = "Alimentação",
        icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE,
        createdAt = 0,
    )

    private val mockIncomeCategory = Category(
        id = 2,
        name = "Salário",
        icon = CategoryLazyIcon("payments"),
        type = Category.Type.INCOME,
        createdAt = 0,
    )

    val concreteBalanceStats = DashboardComponent.ConcreteBalanceStats(
        income = 3200.0,
        expense = 1800.0,
    )

    val pendingBalanceStats = DashboardComponent.PendingBalanceStats(
        pendingIncome = 500.0,
        pendingExpense = 300.0,
    )

    val accountsOverview = DashboardComponent.AccountsOverview(
        accounts = listOf(
            DashboardAccountUi(account = mockAccount, balance = 2500.0),
            DashboardAccountUi(
                account = Account(
                    id = 2,
                    name = "Poupança",
                    iconKey = "piggy_bank",
                    createdAt = 0
                ),
                balance = 1200.0,
            ),
        ),
    )

    val creditCardsPager = DashboardComponent.CreditCardsPager.Content(
        creditCards = listOf(
            CreditCardUi(
                creditCard = CreditCard(
                    id = 1,
                    name = "Nubank",
                    limit = 5000.0,
                    closingDay = 5,
                    dueDay = 12,
                    iconKey = "card",
                    createdAt = 0,
                ),
                invoiceUi = null,
            ),
        ),
    )

    val spendingPager = DashboardComponent.SpendingPager(
        categorySpending = listOf(
            CategorySpending(category = mockExpenseCategory, amount = 450.0, percentage = 0.35),
            CategorySpending(
                category = Category(
                    id = 3,
                    name = "Transporte",
                    icon = CategoryLazyIcon("directions_car"),
                    type = Category.Type.EXPENSE,
                    createdAt = 0,
                ),
                amount = 280.0,
                percentage = 0.22,
            ),
        ),
        budgetProgress = listOf(
            BudgetProgress(
                budget = Budget(
                    id = 1,
                    title = "Alimentação",
                    categories = listOf(mockExpenseCategory),
                    iconKey = "shopping",
                    amount = 600.0,
                    createdAt = 0,
                ),
                spent = 450.0,
            ),
        ),
    )

    val pendingRecurring = DashboardComponent.PendingRecurring(
        recurringList = listOf(
            Recurring(
                id = 1,
                type = Transaction.Type.EXPENSE,
                amount = 49.90,
                title = "Netflix",
                dayOfMonth = 15,
                category = null,
                account = mockAccount,
                creditCard = null,
                createdAt = 0,
            ),
            Recurring(
                id = 2,
                type = Transaction.Type.INCOME,
                amount = 3500.0,
                title = "Salário",
                dayOfMonth = 5,
                category = mockIncomeCategory,
                account = mockAccount,
                creditCard = null,
                createdAt = 0,
            ),
        ),
    )

    val recents = DashboardComponent.Recents(
        operations = listOf(
            Operation(
                id = 1,
                kind = Operation.Kind.TRANSACTION,
                title = "Supermercado",
                date = LocalDate(2026, 3, 20),
                category = mockExpenseCategory,
                sourceAccount = mockAccount,
                transactions = listOf(
                    Transaction(
                        id = 1,
                        type = Transaction.Type.EXPENSE,
                        amount = 156.80,
                        title = "Supermercado",
                        date = LocalDate(2026, 3, 20),
                        category = mockExpenseCategory,
                        account = mockAccount,
                    ),
                ),
            ),
            Operation(
                id = 2,
                kind = Operation.Kind.TRANSACTION,
                title = "Salário",
                date = LocalDate(2026, 3, 5),
                sourceAccount = mockAccount,
                transactions = listOf(
                    Transaction(
                        id = 2,
                        type = Transaction.Type.INCOME,
                        amount = 3500.0,
                        title = "Salário",
                        date = LocalDate(2026, 3, 5),
                        account = mockAccount,
                    ),
                ),
            ),
            Operation(
                id = 3,
                kind = Operation.Kind.TRANSACTION,
                title = "Spotify",
                date = LocalDate(2026, 3, 1),
                category = mockExpenseCategory,
                sourceAccount = mockAccount,
                transactions = listOf(
                    Transaction(
                        id = 3,
                        type = Transaction.Type.EXPENSE,
                        amount = 21.90,
                        title = "Spotify",
                        date = LocalDate(2026, 3, 1),
                        category = mockExpenseCategory,
                        account = mockAccount,
                    ),
                ),
            ),
        ),
        hasMore = true,
    )

    val quickActions = DashboardComponent.QuickActions(
        actions = QuickActionType.entries,
    )
}
