package com.neoutils.finsight.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The controller binding, which only the JVM desktop target can satisfy with a server.
 */
expect val mcpPlatformModule: Module

val mcpModule = module {
    includes(mcpPlatformModule)
}
