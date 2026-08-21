package com.neoutils.finsight.di

import org.koin.core.module.Module

val appModules: List<Module> = listOf(
    databaseModule,
    ledgerModule,
    commonModule,
    modelModule,
    designsystemModule,
    analyticsModule,
    crashlyticsModule,
    authModule,
    shellModule,
    reportModule,
    settingsModule,
    supportModule,
    dashboardModule,
    categoriesModule,
    creditCardsModule,
    mcpModule,
    transactionsModule,
    accountsModule,
    budgetsModule,
    recurringModule,
)
