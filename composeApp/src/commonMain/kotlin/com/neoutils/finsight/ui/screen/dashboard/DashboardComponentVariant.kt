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

sealed interface DashboardComponentVariant {
    val component: DashboardComponent
    val key: String get() = component.key

    sealed interface TotalBalance : DashboardComponentVariant {
        override val component: DashboardComponent.TotalBalance

        data class Viewing(
            override val component: DashboardComponent.TotalBalance,
        ) : TotalBalance

        data object Preview : TotalBalance {
            override val component = DashboardComponent.TotalBalance(amount = 5432.10)
        }
    }

    sealed interface ConcreteBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.ConcreteBalanceStats

        data class Viewing(
            override val component: DashboardComponent.ConcreteBalanceStats,
        ) : ConcreteBalanceStats

        data object Preview : ConcreteBalanceStats {
            override val component = DashboardComponent.ConcreteBalanceStats(
                income = 3200.0,
                expense = 1800.0,
            )
        }
    }

    sealed interface PendingBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.PendingBalanceStats

        data class Viewing(
            override val component: DashboardComponent.PendingBalanceStats,
        ) : PendingBalanceStats

        data object Preview : PendingBalanceStats {
            override val component = DashboardComponent.PendingBalanceStats(
                pendingIncome = 500.0,
                pendingExpense = 300.0,
            )
        }
    }

    sealed interface CreditCardBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardBalanceStats

        data class Viewing(
            override val component: DashboardComponent.CreditCardBalanceStats,
        ) : CreditCardBalanceStats

        data object Preview : CreditCardBalanceStats {
            override val component = DashboardComponent.CreditCardBalanceStats(
                payment = 640.0,
                expense = 2150.0,
            )
        }
    }

    sealed interface AccountsOverview : DashboardComponentVariant {
        override val component: DashboardComponent.AccountsOverview

        data class Viewing(
            override val component: DashboardComponent.AccountsOverview,
        ) : AccountsOverview

        data object Preview : AccountsOverview {
            override val component = DashboardComponent.AccountsOverview(
                accounts = listOf(
                    DashboardAccountUi(
                        account = Account(
                            id = 1,
                            name = "Conta Principal",
                            iconKey = "wallet",
                            isDefault = true,
                            createdAt = 0,
                        ),
                        balance = 2500.0
                    ),
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
        }
    }

    sealed interface CreditCardsPager : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardsPager

        data class Viewing(
            override val component: DashboardComponent.CreditCardsPager,
        ) : CreditCardsPager

        data object Preview : CreditCardsPager {
            override val component = DashboardComponent.CreditCardsPager.Content(
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
        }
    }

    sealed interface SpendingByCategory : DashboardComponentVariant {
        override val component: DashboardComponent.SpendingByCategory

        data class Viewing(
            override val component: DashboardComponent.SpendingByCategory,
        ) : SpendingByCategory

        data object Preview : SpendingByCategory {
            override val component = DashboardComponent.SpendingByCategory(
                categorySpending = listOf(
                    CategorySpending(
                        category = Category(
                            id = 1,
                            name = "Alimentação",
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
                            name = "Transporte",
                            icon = CategoryLazyIcon("directions_car"),
                            type = Category.Type.EXPENSE,
                            createdAt = 0,
                        ),
                        amount = 280.0,
                        percentage = 38.36,
                    ),
                ),
            )
        }
    }

    sealed interface IncomeByCategory : DashboardComponentVariant {
        override val component: DashboardComponent.IncomeByCategory

        data class Viewing(
            override val component: DashboardComponent.IncomeByCategory,
        ) : IncomeByCategory

        data object Preview : IncomeByCategory {
            override val component = DashboardComponent.IncomeByCategory(
                categoryIncome = listOf(
                    CategorySpending(
                        category = Category(
                            id = 2,
                            name = "Salário",
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
        }
    }

    sealed interface Budgets : DashboardComponentVariant {
        override val component: DashboardComponent.Budgets

        data class Viewing(
            override val component: DashboardComponent.Budgets,
        ) : Budgets

        data object Preview : Budgets {
            override val component = DashboardComponent.Budgets(
                budgetProgress = listOf(
                    BudgetProgress(
                        budget = Budget(
                            id = 1,
                            title = "Alimentação",
                            categories = listOf(
                                Category(
                                    id = 1,
                                    name = "Alimentação",
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
        }
    }

    sealed interface PendingRecurring : DashboardComponentVariant {
        override val component: DashboardComponent.PendingRecurring

        data class Viewing(
            override val component: DashboardComponent.PendingRecurring,
        ) : PendingRecurring

        data object Preview : PendingRecurring {
            override val component = DashboardComponent.PendingRecurring(
                recurringList = listOf(
                    Recurring(
                        id = 1,
                        type = Transaction.Type.EXPENSE,
                        amount = 49.90,
                        title = "Netflix",
                        dayOfMonth = 15,
                        category = null,
                        account = Account(
                            id = 1,
                            name = "Conta Principal",
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
                        title = "Salário",
                        dayOfMonth = 5,
                        category = Category(
                            id = 2,
                            name = "Salário",
                            icon = CategoryLazyIcon("payments"),
                            type = Category.Type.INCOME,
                            createdAt = 0,
                            ),
                        account = Account(
                            id = 1,
                            name = "Conta Principal",
                            iconKey = "wallet",
                            isDefault = true,
                            createdAt = 0,
                        ),
                        creditCard = null,
                        createdAt = 0,
                    ),
                ),
            )
        }
    }

    sealed interface Recents : DashboardComponentVariant {
        override val component: DashboardComponent.Recents

        data class Viewing(
            override val component: DashboardComponent.Recents,
        ) : Recents

        data object Preview : Recents {
            override val component = DashboardComponent.Recents(
                operations = listOf(
                    Operation(
                        id = 1,
                        kind = Operation.Kind.TRANSACTION,
                        title = "Supermercado",
                        date = LocalDate(2026, 3, 20),
                        category = Category(
                            id = 1,
                            name = "Alimentação",
                            icon = CategoryLazyIcon("shopping"),
                            type = Category.Type.EXPENSE,
                            createdAt = 0,
                        ),
                        sourceAccount = Account(
                            id = 1,
                            name = "Conta Principal",
                            iconKey = "wallet",
                            isDefault = true,
                            createdAt = 0,
                        ),
                        transactions = listOf(
                            Transaction(
                                id = 1,
                                type = Transaction.Type.EXPENSE,
                                amount = 156.80,
                                title = "Supermercado",
                                date = LocalDate(2026, 3, 20),
                                category = Category(
                                    id = 1,
                                    name = "Alimentação",
                                    icon = CategoryLazyIcon("shopping"),
                                    type = Category.Type.EXPENSE,
                                    createdAt = 0,
                                ),
                                account = Account(
                                    id = 1,
                                    name = "Conta Principal",
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
                        title = "Salário",
                        date = LocalDate(2026, 3, 5),
                        sourceAccount = Account(
                            id = 1,
                            name = "Conta Principal",
                            iconKey = "wallet",
                            isDefault = true,
                            createdAt = 0,
                        ),
                        transactions = listOf(
                            Transaction(
                                id = 2,
                                type = Transaction.Type.INCOME,
                                amount = 3500.0,
                                title = "Salário",
                                date = LocalDate(2026, 3, 5),
                                account = Account(
                                    id = 1,
                                    name = "Conta Principal",
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
                        title = "Spotify",
                        date = LocalDate(2026, 3, 1),
                        category = Category(
                            id = 1,
                            name = "Alimentação",
                            icon = CategoryLazyIcon("shopping"),
                            type = Category.Type.EXPENSE,
                            createdAt = 0,
                        ),
                        sourceAccount = Account(
                            id = 1,
                            name = "Conta Principal",
                            iconKey = "wallet",
                            isDefault = true,
                            createdAt = 0,
                        ),
                        transactions = listOf(
                            Transaction(
                                id = 3,
                                type = Transaction.Type.EXPENSE,
                                amount = 21.90,
                                title = "Spotify",
                                date = LocalDate(2026, 3, 1),
                                category = Category(
                                    id = 1,
                                    name = "Alimentação",
                                    icon = CategoryLazyIcon("shopping"),
                                    type = Category.Type.EXPENSE,
                                    createdAt = 0,
                                ),
                                account = Account(
                                    id = 1,
                                    name = "Conta Principal",
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
        }
    }

    sealed interface QuickActions : DashboardComponentVariant {
        override val component: DashboardComponent.QuickActions

        data class Viewing(
            override val component: DashboardComponent.QuickActions,
        ) : QuickActions

        data object Preview : QuickActions {
            override val component = DashboardComponent.QuickActions(
                actions = QuickActionType.entries,
            )
        }
    }

    companion object {
        fun forComponent(component: DashboardComponent): DashboardComponentVariant = when (component) {
            is DashboardComponent.TotalBalance -> TotalBalance.Viewing(component)
            is DashboardComponent.ConcreteBalanceStats -> ConcreteBalanceStats.Viewing(component)
            is DashboardComponent.PendingBalanceStats -> PendingBalanceStats.Viewing(component)
            is DashboardComponent.CreditCardBalanceStats -> CreditCardBalanceStats.Viewing(component)
            is DashboardComponent.AccountsOverview -> AccountsOverview.Viewing(component)
            is DashboardComponent.CreditCardsPager -> CreditCardsPager.Viewing(component)
            is DashboardComponent.SpendingByCategory -> SpendingByCategory.Viewing(component)
            is DashboardComponent.IncomeByCategory -> IncomeByCategory.Viewing(component)
            is DashboardComponent.Budgets -> Budgets.Viewing(component)
            is DashboardComponent.PendingRecurring -> PendingRecurring.Viewing(component)
            is DashboardComponent.Recents -> Recents.Viewing(component)
            is DashboardComponent.QuickActions -> QuickActions.Viewing(component)
        }

        fun previewForKey(key: String): DashboardComponentVariant? = when (key) {
            DashboardComponentKey.TOTAL_BALANCE.value -> TotalBalance.Preview
            DashboardComponentKey.CONCRETE_BALANCE_STATS.value -> ConcreteBalanceStats.Preview
            DashboardComponentKey.PENDING_BALANCE_STATS.value -> PendingBalanceStats.Preview
            DashboardComponentKey.CREDIT_CARD_BALANCE_STATS.value -> CreditCardBalanceStats.Preview
            DashboardComponentKey.ACCOUNTS_OVERVIEW.value -> AccountsOverview.Preview
            DashboardComponentKey.CREDIT_CARDS_PAGER.value -> CreditCardsPager.Preview
            DashboardComponentKey.SPENDING_BY_CATEGORY.value -> SpendingByCategory.Preview
            DashboardComponentKey.INCOME_BY_CATEGORY.value -> IncomeByCategory.Preview
            DashboardComponentKey.BUDGETS.value -> Budgets.Preview
            DashboardComponentKey.PENDING_RECURRING.value -> PendingRecurring.Preview
            DashboardComponentKey.RECENTS.value -> Recents.Preview
            DashboardComponentKey.QUICK_ACTIONS.value -> QuickActions.Preview
            else -> null
        }
    }
}
