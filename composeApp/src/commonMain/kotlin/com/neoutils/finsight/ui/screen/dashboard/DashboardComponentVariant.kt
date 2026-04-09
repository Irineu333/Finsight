package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.UiText

sealed interface DashboardComponentVariant {
    val component: DashboardComponent
    val config: Map<String, String>
    val title: UiText
    val key: String get() = component.key

    sealed interface TotalBalance : DashboardComponentVariant {
        override val component: DashboardComponent.TotalBalance
        override val title: UiText get() = UiText.Res(Res.string.component_total_balance)

        data class Viewing(
            override val component: DashboardComponent.TotalBalance,
            override val config: Map<String, String>,
        ) : TotalBalance

        data class Preview(
            override val component: DashboardComponent.TotalBalance,
            override val config: Map<String, String> = emptyMap(),
        ) : TotalBalance
    }

    sealed interface ConcreteBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.ConcreteBalanceStats
        override val title: UiText get() = UiText.Res(Res.string.component_balance_stats)

        data class Viewing(
            override val component: DashboardComponent.ConcreteBalanceStats,
            override val config: Map<String, String>,
        ) : ConcreteBalanceStats

        data class Preview(
            override val component: DashboardComponent.ConcreteBalanceStats,
            override val config: Map<String, String> = emptyMap(),
        ) : ConcreteBalanceStats
    }

    sealed interface PendingBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.PendingBalanceStats
        override val title: UiText get() = UiText.Res(Res.string.component_pending_balance)

        data class Viewing(
            override val component: DashboardComponent.PendingBalanceStats,
            override val config: Map<String, String>,
        ) : PendingBalanceStats

        data class Preview(
            override val component: DashboardComponent.PendingBalanceStats,
            override val config: Map<String, String> = emptyMap(),
        ) : PendingBalanceStats
    }

    sealed interface CreditCardBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardBalanceStats
        override val title: UiText get() = UiText.Res(Res.string.component_credit_card_balance_stats)

        data class Viewing(
            override val component: DashboardComponent.CreditCardBalanceStats,
            override val config: Map<String, String>,
        ) : CreditCardBalanceStats

        data class Preview(
            override val component: DashboardComponent.CreditCardBalanceStats,
            override val config: Map<String, String> = emptyMap(),
        ) : CreditCardBalanceStats
    }

    sealed interface AccountsOverview : DashboardComponentVariant {
        override val component: DashboardComponent.AccountsOverview
        override val title: UiText get() = UiText.Res(Res.string.component_accounts_overview)

        data class Viewing(
            override val component: DashboardComponent.AccountsOverview,
            override val config: Map<String, String>,
        ) : AccountsOverview

        data class Preview(
            override val component: DashboardComponent.AccountsOverview,
            override val config: Map<String, String> = emptyMap(),
        ) : AccountsOverview
    }

    sealed interface CreditCardsPager : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardsPager
        override val title: UiText get() = UiText.Res(Res.string.component_credit_cards)

        data class Viewing(
            override val component: DashboardComponent.CreditCardsPager,
            override val config: Map<String, String>,
        ) : CreditCardsPager

        data class Preview(
            override val component: DashboardComponent.CreditCardsPager,
            override val config: Map<String, String> = emptyMap(),
        ) : CreditCardsPager
    }

    sealed interface SpendingByCategory : DashboardComponentVariant {
        override val component: DashboardComponent.SpendingByCategory
        override val title: UiText get() = UiText.Res(Res.string.component_spending_by_category)

        data class Viewing(
            override val component: DashboardComponent.SpendingByCategory,
            override val config: Map<String, String>,
        ) : SpendingByCategory

        data class Preview(
            override val component: DashboardComponent.SpendingByCategory,
            override val config: Map<String, String> = emptyMap(),
        ) : SpendingByCategory
    }

    sealed interface IncomeByCategory : DashboardComponentVariant {
        override val component: DashboardComponent.IncomeByCategory
        override val title: UiText get() = UiText.Res(Res.string.component_income_by_category)

        data class Viewing(
            override val component: DashboardComponent.IncomeByCategory,
            override val config: Map<String, String>,
        ) : IncomeByCategory

        data class Preview(
            override val component: DashboardComponent.IncomeByCategory,
            override val config: Map<String, String> = emptyMap(),
        ) : IncomeByCategory
    }

    sealed interface Budgets : DashboardComponentVariant {
        override val component: DashboardComponent.Budgets
        override val title: UiText get() = UiText.Res(Res.string.component_budgets)

        data class Viewing(
            override val component: DashboardComponent.Budgets,
            override val config: Map<String, String>,
        ) : Budgets

        data class Preview(
            override val component: DashboardComponent.Budgets,
            override val config: Map<String, String> = emptyMap(),
        ) : Budgets
    }

    sealed interface PendingRecurring : DashboardComponentVariant {
        override val component: DashboardComponent.PendingRecurring
        override val title: UiText get() = UiText.Res(Res.string.component_pending_recurring)

        data class Viewing(
            override val component: DashboardComponent.PendingRecurring,
            override val config: Map<String, String>,
        ) : PendingRecurring

        data class Preview(
            override val component: DashboardComponent.PendingRecurring,
            override val config: Map<String, String> = emptyMap(),
        ) : PendingRecurring
    }

    sealed interface Recents : DashboardComponentVariant {
        override val component: DashboardComponent.Recents
        override val title: UiText get() = UiText.Res(Res.string.component_recents)

        data class Viewing(
            override val component: DashboardComponent.Recents,
            override val config: Map<String, String>,
        ) : Recents

        data class Preview(
            override val component: DashboardComponent.Recents,
            override val config: Map<String, String> = emptyMap(),
        ) : Recents
    }

    sealed interface QuickActions : DashboardComponentVariant {
        override val component: DashboardComponent.QuickActions
        override val title: UiText get() = UiText.Res(Res.string.component_quick_actions)

        data class Viewing(
            override val component: DashboardComponent.QuickActions,
            override val config: Map<String, String>,
        ) : QuickActions

        data class Preview(
            override val component: DashboardComponent.QuickActions,
            override val config: Map<String, String> = emptyMap(),
        ) : QuickActions
    }
}
