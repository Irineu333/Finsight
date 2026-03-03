package com.neoutils.finsight.di

import org.koin.core.module.Module
import org.koin.dsl.module

val reportModule = module {
    includes(reportPlatformModule)
}

expect val reportPlatformModule: Module
