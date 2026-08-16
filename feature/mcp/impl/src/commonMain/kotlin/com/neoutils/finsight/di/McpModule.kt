package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.database.repository.AgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The controller binding, which only the JVM desktop target can satisfy with a server.
 */
expect val mcpPlatformModule: Module

val mcpModule = module {
    includes(mcpPlatformModule)

    // The log is bound on every platform, unlike the server: it is a table of the shared
    // database, and reading it asks for nothing a platform might not have. Where no agent
    // ever writes, it simply stays empty.
    factory { AgentActivityMapper() }
    single<IAgentActivityRepository> { AgentActivityRepository(get(), get(), get()) }
}
