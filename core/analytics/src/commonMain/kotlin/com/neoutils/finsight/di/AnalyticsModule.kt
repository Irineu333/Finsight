package com.neoutils.finsight.di

import org.koin.core.module.Module
import org.koin.dsl.module

val analyticsModule = module {
    includes(analyticsPlatformModule)
}

expect val analyticsPlatformModule: Module
