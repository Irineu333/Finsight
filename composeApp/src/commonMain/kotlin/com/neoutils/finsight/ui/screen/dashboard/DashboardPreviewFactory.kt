package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

class DashboardPreviewFactory {
    suspend fun createPreview(key: String): DashboardComponentVariant? = when (key) {
        DashboardComponentType.TOTAL_BALANCE.key -> {
            DashboardComponentVariant.TotalBalance.Preview(
                component = DashboardComponent.TotalBalance(
                    amount = 5432.10,
                ),
            )
        }

        DashboardComponentType.CONCRETE_BALANCE_STATS.key -> {
            DashboardComponentVariant.ConcreteBalanceStats.Preview(
                component = DashboardComponent.ConcreteBalanceStats(
                    income = 3200.0,
                    expense = 1800.0,
                ),
            )
        }

        DashboardComponentType.PENDING_BALANCE_STATS.key -> {
            DashboardComponentVariant.PendingBalanceStats.Preview(
                component = DashboardComponent.PendingBalanceStats(
                    pendingIncome = 500.0,
                    pendingExpense = 300.0,
                ),
            )
        }

        DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key -> {
            DashboardComponentVariant.CreditCardBalanceStats.Preview(
                component = DashboardComponent.CreditCardBalanceStats(
                    payment = 640.0,
                    expense = 2150.0,
                ),
            )
        }

        DashboardComponentType.ACCOUNTS_OVERVIEW.key -> {
            DashboardComponentVariant.AccountsOverview.Preview(
                component = DashboardComponent.AccountsOverview(
                    accounts = listOf(
                        DashboardAccountUi(
                            account = Account(
                                id = 1,
                                name = getString(Res.string.preview_account_main),
                                iconKey = "wallet",
                                isDefault = true,
                                createdAt = 0,
                            ),
                            balance = 2500.0
                        ),
                        DashboardAccountUi(
                            account = Account(
                                id = 2,
                                name = getString(Res.string.preview_account_savings),
                                iconKey = "piggy_bank",
                                createdAt = 0
                            ),
                            balance = 1200.0,
                        ),
                    ),
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        DashboardComponentType.CREDIT_CARDS_PAGER.key -> {
            DashboardComponentVariant.CreditCardsPager.Preview(
                component = DashboardComponent.CreditCardsPager.Content(
                    creditCards = listOf(
                        CreditCardUi(
                            creditCard = CreditCard(
                                id = 1,
                                name = getString(Res.string.preview_card_nubank),
                                limit = 5000.0,
                                closingDay = 5,
                                dueDay = 12,
                                iconKey = "card",
                                createdAt = 0,
                            ),
                            invoiceUi = null,
                        ),
                    ),
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        DashboardComponentType.SPENDING_BY_CATEGORY.key -> {
            DashboardComponentVariant.SpendingByCategory.Preview(
                component = DashboardComponent.SpendingByCategory(
                    categorySpending = listOf(
                        CategorySpending(
                            category = Category(
                                id = 1,
                                name = getString(Res.string.preview_category_food),
                                icon = CategoryLazyIcon("shopping"),
                                type = Category.Type.EXPENSE,
                                createdAt = 0,
                            ),
                            amount = 450.0,
                            percentage = 61.64
                        ),
                        CategorySpending(
                            category = Category(
                                id = 3,
                                name = getString(Res.string.preview_category_transport),
                                icon = CategoryLazyIcon("directions_car"),
                                type = Category.Type.EXPENSE,
                                createdAt = 0,
                            ),
                            amount = 280.0,
                            percentage = 38.36,
                        ),
                    ),
                ),
            )
        }

        DashboardComponentType.INCOME_BY_CATEGORY.key -> {
            DashboardComponentVariant.IncomeByCategory.Preview(
                component = DashboardComponent.IncomeByCategory(
                    categoryIncome = listOf(
                        CategorySpending(
                            category = Category(
                                id = 2,
                                name = getString(Res.string.preview_category_salary),
                                icon = CategoryLazyIcon("payments"),
                                type = Category.Type.INCOME,
                                createdAt = 0,
                            ),
                            amount = 3200.0,
                            percentage = 84.21
                        ),
                        CategorySpending(
                            category = Category(
                                id = 4,
                                name = getString(Res.string.preview_category_freelance),
                                icon = CategoryLazyIcon("laptop"),
                                type = Category.Type.INCOME,
                                createdAt = 0,
                            ),
                            amount = 600.0,
                            percentage = 15.79,
                        ),
                    ),
                )
            )
        }

        DashboardComponentType.BUDGETS.key -> {
            DashboardComponentVariant.Budgets.Preview(
                component = DashboardComponent.Budgets(
                    budgetProgress = listOf(
                        BudgetProgress(
                            budget = Budget(
                                id = 1,
                                title = getString(Res.string.preview_budget_food),
                                categories = listOf(
                                    Category(
                                        id = 1,
                                        name = getString(Res.string.preview_category_food),
                                        icon = CategoryLazyIcon("shopping"),
                                        type = Category.Type.EXPENSE,
                                        createdAt = 0,
                                    )
                                ),
                                iconKey = "shopping",
                                amount = 600.0,
                                createdAt = 0,
                            ),
                            spent = 450.0,
                        ),
                    ),
                )
            )
        }

        DashboardComponentType.PENDING_RECURRING.key -> {
            DashboardComponentVariant.PendingRecurring.Preview(
                component = DashboardComponent.PendingRecurring(
                    recurringList = listOf(
                        Recurring(
                            id = 1,
                            type = Transaction.Type.EXPENSE,
                            amount = 49.90,
                            title = getString(Res.string.preview_transaction_netflix),
                            dayOfMonth = 15,
                            category = null,
                            account = Account(
                                id = 1,
                                name = getString(Res.string.preview_account_main),
                                iconKey = "wallet",
                                isDefault = true,
                                createdAt = 0,
                            ),
                            creditCard = null,
                            createdAt = 0,
                        ),
                        Recurring(
                            id = 2,
                            type = Transaction.Type.INCOME,
                            amount = 3500.0,
                            title = getString(Res.string.preview_category_salary),
                            dayOfMonth = 5,
                            category = Category(
                                id = 2,
                                name = getString(Res.string.preview_category_salary),
                                icon = CategoryLazyIcon("payments"),
                                type = Category.Type.INCOME,
                                createdAt = 0,
                            ),
                            account = Account(
                                id = 1,
                                name = getString(Res.string.preview_account_main),
                                iconKey = "wallet",
                                isDefault = true,
                                createdAt = 0,
                            ),
                            creditCard = null,
                            createdAt = 0,
                        ),
                    ),
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        DashboardComponentType.RECENTS.key -> {
            DashboardComponentVariant.Recents.Preview(
                component = DashboardComponent.Recents(
                    operations = listOf(
                        Operation(
                            id = 1,
                            kind = Operation.Kind.TRANSACTION,
                            title = getString(Res.string.preview_transaction_supermarket),
                            date = LocalDate(2026, 3, 20),
                            category = Category(
                                id = 1,
                                name = getString(Res.string.preview_category_food),
                                icon = CategoryLazyIcon("shopping"),
                                type = Category.Type.EXPENSE,
                                createdAt = 0,
                            ),
                            sourceAccount = Account(
                                id = 1,
                                name = getString(Res.string.preview_account_main),
                                iconKey = "wallet",
                                isDefault = true,
                                createdAt = 0,
                            ),
                            transactions = listOf(
                                Transaction(
                                    id = 1,
                                    type = Transaction.Type.EXPENSE,
                                    amount = 156.80,
                                    title = getString(Res.string.preview_transaction_supermarket),
                                    date = LocalDate(2026, 3, 20),
                                    category = Category(
                                        id = 1,
                                        name = getString(Res.string.preview_category_food),
                                        icon = CategoryLazyIcon("shopping"),
                                        type = Category.Type.EXPENSE,
                                        createdAt = 0,
                                    ),
                                    account = Account(
                                        id = 1,
                                        name = getString(Res.string.preview_account_main),
                                        iconKey = "wallet",
                                        isDefault = true,
                                        createdAt = 0,
                                    ),
                                ),
                            ),
                        ),
                        Operation(
                            id = 2,
                            kind = Operation.Kind.TRANSACTION,
                            title = getString(Res.string.preview_category_salary),
                            date = LocalDate(2026, 3, 5),
                            sourceAccount = Account(
                                id = 1,
                                name = getString(Res.string.preview_account_main),
                                iconKey = "wallet",
                                isDefault = true,
                                createdAt = 0,
                            ),
                            transactions = listOf(
                                Transaction(
                                    id = 2,
                                    type = Transaction.Type.INCOME,
                                    amount = 3500.0,
                                    title = getString(Res.string.preview_category_salary),
                                    date = LocalDate(2026, 3, 5),
                                    account = Account(
                                        id = 1,
                                        name = getString(Res.string.preview_account_main),
                                        iconKey = "wallet",
                                        isDefault = true,
                                        createdAt = 0,
                                    ),
                                ),
                            ),
                        ),
                        Operation(
                            id = 3,
                            kind = Operation.Kind.TRANSACTION,
                            title = getString(Res.string.preview_transaction_spotify),
                            date = LocalDate(2026, 3, 1),
                            category = Category(
                                id = 1,
                                name = getString(Res.string.preview_category_food),
                                icon = CategoryLazyIcon("shopping"),
                                type = Category.Type.EXPENSE,
                                createdAt = 0,
                            ),
                            sourceAccount = Account(
                                id = 1,
                                name = getString(Res.string.preview_account_main),
                                iconKey = "wallet",
                                isDefault = true,
                                createdAt = 0,
                            ),
                            transactions = listOf(
                                Transaction(
                                    id = 3,
                                    type = Transaction.Type.EXPENSE,
                                    amount = 21.90,
                                    title = getString(Res.string.preview_transaction_spotify),
                                    date = LocalDate(2026, 3, 1),
                                    category = Category(
                                        id = 1,
                                        name = getString(Res.string.preview_category_food),
                                        icon = CategoryLazyIcon("shopping"),
                                        type = Category.Type.EXPENSE,
                                        createdAt = 0,
                                    ),
                                    account = Account(
                                        id = 1,
                                        name = getString(Res.string.preview_account_main),
                                        iconKey = "wallet",
                                        isDefault = true,
                                        createdAt = 0,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    hasMore = true,
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        DashboardComponentType.QUICK_ACTIONS.key -> {
            DashboardComponentVariant.QuickActions.Preview(
                component = DashboardComponent.QuickActions(
                    actions = QuickActionType.entries,
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        else -> null
    }
}
