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

        data class Preview(
            override val component: DashboardComponent.TotalBalance
        ) : TotalBalance
    }

    sealed interface ConcreteBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.ConcreteBalanceStats

        data class Viewing(
            override val component: DashboardComponent.ConcreteBalanceStats,
        ) : ConcreteBalanceStats

        data class Preview(
            override val component: DashboardComponent.ConcreteBalanceStats,
        ) : ConcreteBalanceStats
    }

    sealed interface PendingBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.PendingBalanceStats

        data class Viewing(
            override val component: DashboardComponent.PendingBalanceStats,
        ) : PendingBalanceStats

        data class Preview(
            override val component: DashboardComponent.PendingBalanceStats,
        ) : PendingBalanceStats
    }

    sealed interface CreditCardBalanceStats : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardBalanceStats

        data class Viewing(
            override val component: DashboardComponent.CreditCardBalanceStats,
        ) : CreditCardBalanceStats

        data class Preview(
            override val component: DashboardComponent.CreditCardBalanceStats,
        ) : CreditCardBalanceStats
    }

    sealed interface AccountsOverview : DashboardComponentVariant {
        override val component: DashboardComponent.AccountsOverview

        data class Viewing(
            override val component: DashboardComponent.AccountsOverview,
        ) : AccountsOverview

        data class Preview(
            override val component: DashboardComponent.AccountsOverview,
        ) : AccountsOverview
    }

    sealed interface CreditCardsPager : DashboardComponentVariant {
        override val component: DashboardComponent.CreditCardsPager

        data class Viewing(
            override val component: DashboardComponent.CreditCardsPager,
        ) : CreditCardsPager

            data class Preview(
            override val component: DashboardComponent.CreditCardsPager,
        ) : CreditCardsPager
    }

    sealed interface SpendingByCategory : DashboardComponentVariant {
        override val component: DashboardComponent.SpendingByCategory

        data class Viewing(
            override val component: DashboardComponent.SpendingByCategory,
        ) : SpendingByCategory

        data class Preview(
            override val component: DashboardComponent.SpendingByCategory,
        ) : SpendingByCategory
    }

    sealed interface IncomeByCategory : DashboardComponentVariant {
        override val component: DashboardComponent.IncomeByCategory

        data class Viewing(
            override val component: DashboardComponent.IncomeByCategory,
        ) : IncomeByCategory

        data class Preview(
            override val component: DashboardComponent.IncomeByCategory,
        ) : IncomeByCategory
    }

    sealed interface Budgets : DashboardComponentVariant {
        override val component: DashboardComponent.Budgets

        data class Viewing(
            override val component: DashboardComponent.Budgets,
        ) : Budgets

        data class Preview(
            override val component: DashboardComponent.Budgets,
        ) : Budgets
    }

    sealed interface PendingRecurring : DashboardComponentVariant {
        override val component: DashboardComponent.PendingRecurring

        data class Viewing(
            override val component: DashboardComponent.PendingRecurring,
        ) : PendingRecurring

        data class Preview(
            override val component: DashboardComponent.PendingRecurring,
        ) : PendingRecurring
    }

    sealed interface Recents : DashboardComponentVariant {
        override val component: DashboardComponent.Recents

        data class Viewing(
            override val component: DashboardComponent.Recents,
        ) : Recents

        data class Preview(
            override val component: DashboardComponent.Recents,
        ) : Recents
    }

    sealed interface QuickActions : DashboardComponentVariant {
        override val component: DashboardComponent.QuickActions

        data class Viewing(
            override val component: DashboardComponent.QuickActions,
        ) : QuickActions

        data class Preview(
            override val component: DashboardComponent.QuickActions,
        ) : QuickActions
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
    }
}
