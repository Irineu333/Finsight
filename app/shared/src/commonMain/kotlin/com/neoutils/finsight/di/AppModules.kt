package com.neoutils.finsight.di

import org.koin.core.module.Module

val appModules: List<Module> = listOf(
    databaseModule,
    ledgerModule,
    commonModule,
    designsystemModule,
    analyticsModule,
    crashlyticsModule,
    authModule,
    shellModule,
    reportModule,
    supportModule,
    dashboardModule,
    categoriesModule,
    creditCardsModule,
    transactionsModule,
    accountsModule,
    budgetsModule,
    recurringModule,
)
