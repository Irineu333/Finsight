package com.neoutils.finsight.di

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.mcp.DesktopMcpServerController
import org.koin.core.module.Module
import org.koin.dsl.module

actual val mcpPlatformModule: Module = module {
    single<McpServerController> { DesktopMcpServerController() }
}
