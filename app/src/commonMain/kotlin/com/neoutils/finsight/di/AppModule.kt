package com.neoutils.finsight.di

import com.neoutils.finsight.ui.screen.dashboard.DashboardEntry
import com.neoutils.finsight.ui.screen.dashboard.entry.DashboardEntryImpl
import org.koin.dsl.module

val appModule = module {
    single<DashboardEntry> { DashboardEntryImpl() }
}
