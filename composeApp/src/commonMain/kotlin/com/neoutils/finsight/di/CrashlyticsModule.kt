package com.neoutils.finsight.di

import org.koin.core.module.Module
import org.koin.dsl.module

val crashlyticsModule = module {
    includes(crashlyticsPlatformModule)
}

expect val crashlyticsPlatformModule: Module
