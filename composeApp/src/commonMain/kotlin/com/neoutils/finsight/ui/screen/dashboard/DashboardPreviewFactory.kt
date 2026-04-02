package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

interface IDashboardPreviewFactory {
    suspend fun createPreview(key: String): DashboardComponentVariant?
}

class DashboardPreviewFactory : IDashboardPreviewFactory {
    override suspend fun createPreview(key: String): DashboardComponentVariant? = when (key) {
        DashboardComponentKey.TOTAL_BALANCE.value -> DashboardComponentVariant.TotalBalance.Preview(
            DashboardComponent.TotalBalance(amount = 5432.10)
        )

        DashboardComponentKey.CONCRETE_BALANCE_STATS.value -> DashboardComponentVariant.ConcreteBalanceStats.Preview(
            DashboardComponent.ConcreteBalanceStats(
                income = 3200.0,
                expense = 1800.0,
            )
        )

        DashboardComponentKey.PENDING_BALANCE_STATS.value -> DashboardComponentVariant.PendingBalanceStats.Preview(
            DashboardComponent.PendingBalanceStats(
                pendingIncome = 500.0,
                pendingExpense = 300.0,
            )
        )

        DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value -> DashboardComponentVariant.CreditCardBalanceStats.Preview(
            DashboardComponent.CreditCardBalanceStats(
                payment = 640.0,
                expense = 2150.0,
            )
        )

        DashboardComponentKey.ACCOUNTS_OVERVIEW.value -> DashboardComponentVariant.AccountsOverview.Preview(
            DashboardComponent.AccountsOverview(
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
            )
        )

        DashboardComponentKey.CREDIT_CARDS_PAGER.value -> DashboardComponentVariant.CreditCardsPager.Preview(
            DashboardComponent.CreditCardsPager.Content(
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
            )
        )

        DashboardComponentKey.SPENDING_BY_CATEGORY.value -> DashboardComponentVariant.SpendingByCategory.Preview(
            DashboardComponent.SpendingByCategory(
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
            )
        )

        DashboardComponentKey.INCOME_BY_CATEGORY.value -> DashboardComponentVariant.IncomeByCategory.Preview(
            DashboardComponent.IncomeByCategory(
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

        DashboardComponentKey.BUDGETS.value -> DashboardComponentVariant.Budgets.Preview(
            DashboardComponent.Budgets(
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

        DashboardComponentKey.PENDING_RECURRING.value -> DashboardComponentVariant.PendingRecurring.Preview(
            DashboardComponent.PendingRecurring(
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
            )
        )

        DashboardComponentKey.RECENTS.value -> DashboardComponentVariant.Recents.Preview(
            DashboardComponent.Recents(
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
            )
        )

        DashboardComponentKey.QUICK_ACTIONS.value -> DashboardComponentVariant.QuickActions.Preview(
            DashboardComponent.QuickActions(
                actions = QuickActionType.entries,
            )
        )

        else -> null
    }
}
