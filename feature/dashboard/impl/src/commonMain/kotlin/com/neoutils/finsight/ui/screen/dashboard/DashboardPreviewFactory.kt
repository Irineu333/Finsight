package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.extension.localeCurrencyCode
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.CreditCardUi
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

class DashboardPreviewFactory(
    // A preview shows the same widgets the dashboard does, so its figures are built the
    // same way: the consolidated ones leave through the one reducer, exactly as the
    // builder's do. Fabricating a `ConsolidatedAmount` by hand here would be the second
    // place in the app that produces a figure.
    private val consolidateMoney: ConsolidateMoneyUseCase,
    private val navCatalog: NavCatalog,
) {
    /**
     * The currency the fabricated accounts of a preview are denominated in.
     *
     * A preview has to look like the app, and the app denominates an account in the
     * currency of the device's region — so this is the same resolution, not a literal
     * that would render `R$` on a preview beside real accounts reading `$`.
     */
    private val previewCurrency: String = CurrencyCatalog.reduce(localeCurrencyCode())

    private fun amount(value: Double) =
        DisplayAmount.magnitude(value, previewCurrency, isApproximate = false)

    private suspend fun previewAccount() = Account(
        id = 1,
        currency = previewCurrency,
        name = getString(Res.string.preview_account_main),
        iconKey = "wallet",
        isDefault = true,
        createdAt = 0,
    )

    private suspend fun figure(
        value: Double,
        on: LocalDate,
        policy: (Double, String, Boolean) -> DisplayAmount = DisplayAmount::magnitude,
    ): ConsolidatedAmount = consolidateMoney(
        money = MoneyByCurrency.of(previewCurrency, value),
        on = on,
        policy = policy,
    )

    /**
     * @param on the date the preview's consolidated figures are reduced at — the month
     * the user is editing, so a preview reads at the same rates the real widget would.
     */
    suspend fun createPreview(key: String, on: LocalDate): DashboardComponentVariant? = when (key) {
        DashboardComponentType.TOTAL_BALANCE.key -> {
            DashboardComponentVariant.TotalBalance.Preview(
                component = DashboardComponent.TotalBalance(
                    amount = figure(5432.10, on, DisplayAmount::natural),
                ),
            )
        }

        DashboardComponentType.OVERALL_BALANCE_STATS.key -> {
            DashboardComponentVariant.OverallBalanceStats.Preview(
                component = DashboardComponent.OverallBalanceStats(
                    income = figure(3200.0, on),
                    expense = figure(3950.0, on),
                ),
            )
        }

        DashboardComponentType.CONCRETE_BALANCE_STATS.key -> {
            DashboardComponentVariant.ConcreteBalanceStats.Preview(
                component = DashboardComponent.ConcreteBalanceStats(
                    income = figure(3200.0, on),
                    expense = figure(1800.0, on),
                ),
            )
        }

        DashboardComponentType.PENDING_BALANCE_STATS.key -> {
            DashboardComponentVariant.PendingBalanceStats.Preview(
                component = DashboardComponent.PendingBalanceStats(
                    pendingIncome = figure(500.0, on),
                    pendingExpense = figure(300.0, on),
                ),
            )
        }

        DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key -> {
            DashboardComponentVariant.CreditCardBalanceStats.Preview(
                component = DashboardComponent.CreditCardBalanceStats(
                    payment = figure(640.0, on),
                    expense = figure(2150.0, on),
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
                            balance = DisplayAmount.natural(
                                2500.0,
                                previewCurrency,
                                isApproximate = false,
                            ),
                        ),
                        DashboardAccountUi(
                            id = 2,
                            iconKey = "piggy_bank",
                            name = getString(Res.string.preview_account_savings),
                            isDefault = false,
                            balance = DisplayAmount.natural(
                                1200.0,
                                previewCurrency,
                                isApproximate = false,
                            ),
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
                    limits = listOf(amount(5000.0)),
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
                            amount = figure(450.0, on),
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
                            amount = figure(280.0, on),
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
                            amount = figure(3200.0, on),
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
                            amount = figure(600.0, on),
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
                                currency = previewCurrency,
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
                        PendingRecurringUi(
                            amount = amount(49.90),
                            recurring = Recurring(
                                id = 1,
                                type = TransactionType.EXPENSE,
                                amount = 49.90,
                                title = getString(Res.string.preview_transaction_netflix),
                                dayOfMonth = 15,
                                category = null,
                                account = previewAccount(),
                                creditCard = null,
                                createdAt = 0,
                            ),
                        ),
                        PendingRecurringUi(
                            amount = amount(3500.0),
                            recurring = Recurring(
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
                                account = previewAccount(),
                                creditCard = null,
                                createdAt = 0,
                            ),
                        ),
                    ),
                ),
                config = mapOf(DashboardComponentConfig.SHOW_HEADER to "false"),
            )
        }

        DashboardComponentType.RECENTS.key -> {
            val mainAccount = Account(
                id = 1,
                currency = previewCurrency,
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
                currency = previewCurrency,
                name = foodCategory.name,
                type = AccountType.EXPENSE,
                createdAt = 0,
            )
            val salaryAccount = Account(
                id = 102,
                currency = previewCurrency,
                name = getString(Res.string.preview_category_salary),
                type = AccountType.INCOME,
                createdAt = 0,
            )

            DashboardComponentVariant.Recents.Preview(
                component = DashboardComponent.Recents(
                    // Built from the ledger and mapped here, exactly as the builder does,
                    // so the preview cannot read differently from the real section.
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
                    ).mapNotNull { it.toTransactionUi() },
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
