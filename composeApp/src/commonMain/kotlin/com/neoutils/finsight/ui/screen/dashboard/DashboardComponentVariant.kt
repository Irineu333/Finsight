package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Transaction

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