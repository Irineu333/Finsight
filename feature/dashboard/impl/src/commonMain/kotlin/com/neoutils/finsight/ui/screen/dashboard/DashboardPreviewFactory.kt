package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

class DashboardPreviewFactory(
    private val navCatalog: NavCatalog,
) {
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
                            id = 1,
                            iconKey = "wallet",
                            name = getString(Res.string.preview_account_main),
                            isDefault = true,
                            balance = 2500.0,
                        ),
                        DashboardAccountUi(
                            id = 2,
                            iconKey = "piggy_bank",
                            name = getString(Res.string.preview_account_savings),
                            isDefault = false,
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
                            cardId = 1,
                            iconKey = "card",
                            name = getString(Res.string.preview_card_nubank),
                            closingDay = 5,
                            dueDay = 12,
                            limit = 5000.0,
                            invoiceUi = null,
                        ),
                    ),
                    domainInvoices = listOf(null),
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
                            type = TransactionType.EXPENSE,
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
                            type = TransactionType.INCOME,
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
            val mainAccount = Account(
                id = 1,
                name = getString(Res.string.preview_account_main),
                iconKey = "wallet",
                isDefault = true,
                createdAt = 0,
            )
            val foodCategory = Category(
                id = 1,
                name = getString(Res.string.preview_category_food),
                icon = CategoryLazyIcon("shopping"),
                type = Category.Type.EXPENSE,
                createdAt = 0,
            )
            val foodAccount = Account(
                id = 101,
                name = foodCategory.name,
                type = AccountType.EXPENSE,
                createdAt = 0,
            )
            val salaryAccount = Account(
                id = 102,
                name = getString(Res.string.preview_category_salary),
                type = AccountType.INCOME,
                createdAt = 0,
            )

            DashboardComponentVariant.Recents.Preview(
                component = DashboardComponent.Recents(
                    transactions = listOf(
                        Transaction(
                            id = 1,
                            title = getString(Res.string.preview_transaction_supermarket),
                            date = LocalDate(2026, 3, 20),
                            entries = listOf(
                                Entry(id = 1, account = mainAccount, amount = -15680),
                                Entry(id = 2, account = foodAccount, amount = 15680),
                            ),
                        ),
                        Transaction(
                            id = 2,
                            title = getString(Res.string.preview_category_salary),
                            date = LocalDate(2026, 3, 5),
                            entries = listOf(
                                Entry(id = 3, account = mainAccount, amount = 350000),
                                Entry(id = 4, account = salaryAccount, amount = -350000),
                            ),
                        ),
                        Transaction(
                            id = 3,
                            title = getString(Res.string.preview_transaction_spotify),
                            date = LocalDate(2026, 3, 1),
                            entries = listOf(
                                Entry(id = 5, account = mainAccount, amount = -2190),
                                Entry(id = 6, account = foodAccount, amount = 2190),
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
                    actions = navCatalog.destinations.filter { !it.primaryTab },
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        else -> null
    }
}
