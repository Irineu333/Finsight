package com.neoutils.finsight.di

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.mcp.AgentActivityJournal
import com.neoutils.finsight.mcp.DesktopMcpServerController
import com.neoutils.finsight.mcp.McpServerSettings
import com.neoutils.finsight.mcp.mcpTools
import org.koin.core.module.Module
import org.koin.dsl.module

actual val mcpPlatformModule: Module = module {
    single { McpServerSettings(settings = get()) }
    single { AgentActivityJournal(activity = get()) }
    single<McpServerController> {
        DesktopMcpServerController(
            settings = get(),
            journal = get(),
            tools = mcpTools(),
        )
    }
}
