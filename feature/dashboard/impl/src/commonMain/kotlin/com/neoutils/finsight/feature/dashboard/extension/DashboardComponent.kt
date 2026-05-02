package com.neoutils.finsight.feature.dashboard.extension

import com.neoutils.finsight.feature.dashboard.screen.DashboardComponent
import com.neoutils.finsight.feature.dashboard.screen.DashboardComponentVariant

fun DashboardComponent.toViewingVariant(config: Map<String, String>): DashboardComponentVariant = when (this) {
    is DashboardComponent.TotalBalance -> DashboardComponentVariant.TotalBalance.Viewing(this, config)
    is DashboardComponent.ConcreteBalanceStats -> DashboardComponentVariant.ConcreteBalanceStats.Viewing(this, config)
    is DashboardComponent.PendingBalanceStats -> DashboardComponentVariant.PendingBalanceStats.Viewing(this, config)
    is DashboardComponent.CreditCardBalanceStats -> DashboardComponentVariant.CreditCardBalanceStats.Viewing(this, config)
    is DashboardComponent.AccountsOverview -> DashboardComponentVariant.AccountsOverview.Viewing(this, config)
    is DashboardComponent.CreditCardsPager -> DashboardComponentVariant.CreditCardsPager.Viewing(this, config)
    is DashboardComponent.SpendingByCategory -> DashboardComponentVariant.SpendingByCategory.Viewing(this, config)
    is DashboardComponent.IncomeByCategory -> DashboardComponentVariant.IncomeByCategory.Viewing(this, config)
    is DashboardComponent.Budgets -> DashboardComponentVariant.Budgets.Viewing(this, config)
    is DashboardComponent.PendingRecurring -> DashboardComponentVariant.PendingRecurring.Viewing(this, config)
    is DashboardComponent.Recents -> DashboardComponentVariant.Recents.Viewing(this, config)
    is DashboardComponent.QuickActions -> DashboardComponentVariant.QuickActions.Viewing(this, config)
}