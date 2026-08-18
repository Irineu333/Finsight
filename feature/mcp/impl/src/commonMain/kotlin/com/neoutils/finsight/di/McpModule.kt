package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.database.repository.AgentActivityRepository
import com.neoutils.finsight.domain.usecase.ClearAgentActivityUseCase
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.feature.mcp.impl.McpEntryImpl
import com.neoutils.finsight.ui.screen.mcp.McpViewModel
import com.neoutils.finsight.ui.screen.mcpActivity.McpActivityViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
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

    // The section is bound everywhere the route is, which is everywhere: on a platform with no
    // server it resolves the controller that never opens one and says so, rather than offering a
    // switch that turns nothing on.
    factory { ClearAgentActivityUseCase(get()) }

    viewModel { McpViewModel(get(), get(), get(), get()) }
    viewModel { McpActivityViewModel(get(), get(), get()) }

    // How settings, which hosts this section, registers its graph without seeing this module.
    single<McpEntry> { McpEntryImpl() }
}
