package com.neoutils.finsight.di

import org.koin.core.module.Module
import org.koin.dsl.module

val authModule = module {
    includes(authPlatformModule)
}

internal expect val authPlatformModule: Module
