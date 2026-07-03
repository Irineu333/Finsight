package com.neoutils.finsight.di

import org.koin.core.module.Module

/**
 * Agregação Koin do app: módulos dos cores injetáveis + módulos de cada feature.
 * Consumida pelos `startKoin` de cada entry point de plataforma
 * (Android adiciona `androidContext`).
 */
val appModules: List<Module> = listOf(
    databaseModule,
    commonModule,
    designsystemModule,
    analyticsModule,
    crashlyticsModule,
    authModule,
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
