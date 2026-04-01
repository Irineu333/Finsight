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
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate

object DashboardComponentPreviewFactory {
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

    val creditCardBalanceStats = DashboardComponent.CreditCardBalanceStats(
        payment = 640.0,
        expense = 2150.0,
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

    private val mockCategorySpending = listOf(
        CategorySpending(category = mockExpenseCategory, amount = 450.0, percentage = 61.64),
        CategorySpending(
            category = Category(
                id = 3,
                name = "Transporte",
                icon = CategoryLazyIcon("directions_car"),
                type = Category.Type.EXPENSE,
                createdAt = 0,
            ),
            amount = 280.0,
            percentage = 38.36,
        ),
    )

    private val mockBudgetProgress = listOf(
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
    )

    val spendingByCategory = DashboardComponent.SpendingByCategory(
        categorySpending = mockCategorySpending,
    )

    val incomeByCategory = DashboardComponent.IncomeByCategory(
        categoryIncome = listOf(
            CategorySpending(category = mockIncomeCategory, amount = 3200.0, percentage = 84.21),
            CategorySpending(
                category = Category(
                    id = 4,
                    name = "Freelance",
                    icon = CategoryLazyIcon("laptop"),
                    type = Category.Type.INCOME,
                    createdAt = 0,
                ),
                amount = 600.0,
                percentage = 15.79,
            ),
        ),
    )

    val budgets = DashboardComponent.Budgets(
        budgetProgress = mockBudgetProgress,
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
