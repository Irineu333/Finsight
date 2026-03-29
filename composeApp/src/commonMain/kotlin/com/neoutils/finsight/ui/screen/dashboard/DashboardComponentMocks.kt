package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate

object DashboardComponentMocks {

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

    val totalBalance = DashboardComponent.TotalBalance(amount = 5432.10)

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
                account = Account(id = 2, name = "Poupança", iconKey = "piggy_bank", createdAt = 0),
                balance = 1200.0,
            ),
        ),
    )

    val creditCardsPager = DashboardComponent.CreditCardsPager(
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

    fun forKey(key: String): DashboardComponent? = when (key) {
        "total_balance" -> totalBalance
        "balance_stats_concrete" -> concreteBalanceStats
        "balance_stats_pending" -> pendingBalanceStats
        "accounts_overview" -> accountsOverview
        "credit_cards_pager" -> creditCardsPager
        "spending_pager" -> spendingPager
        "pending_recurring" -> pendingRecurring
        "recents" -> recents
        "quick_actions_" -> quickActions
        else -> null
    }
}
