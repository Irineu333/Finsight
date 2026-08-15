package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.database.repository.AgentActivityRepository
import com.neoutils.finsight.database.repository.McpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import com.neoutils.finsight.feature.mcp.api.IMcpServerSettingsRepository
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.feature.mcp.impl.McpEntryImpl
import com.neoutils.finsight.ui.screen.mcp.McpViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The `mcp` feature's Koin module — what the shell aggregates to make the capability's state
 * resolvable.
 *
 * Both repositories are `single` because both own state that must not be duplicated: one holds
 * the `StateFlow` every observer of the configuration reads, and a second instance would give
 * a screen a view that another screen's write never reaches.
 */
val mcpFeatureModule = module {

    single { AgentActivityMapper() }

    single<IMcpServerSettingsRepository> { McpServerSettingsRepository(settings = get()) }

    single<IAgentActivityRepository> {
        AgentActivityRepository(
            dao = get(),
            mapper = get(),
        )
    }

    // The subgraph registration `settings:impl` asks for. `single` because it holds nothing: one
    // instance answers every graph that is built.
    single<McpEntry> { McpEntryImpl() }

    viewModel {
        McpViewModel(
            settingsRepository = get(),
            activityRepository = get(),
            // Bound by `:app:mcp`, which owns the socket — the screen asks the question and never
            // names the answerer.
            serverState = get(),
        )
    }
}
