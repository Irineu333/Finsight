package com.neoutils.finsight.di

import org.koin.core.module.Module

val appModules: List<Module> = listOf(
    databaseModule,
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
