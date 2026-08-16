package com.neoutils.finsight.di

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.mcp.AgentActivityJournal
import com.neoutils.finsight.mcp.DesktopMcpServerController
import com.neoutils.finsight.mcp.McpServerSettings
import com.neoutils.finsight.mcp.McpToolDependencies
import com.neoutils.finsight.mcp.mcpTools
import org.koin.core.module.Module
import org.koin.dsl.module

actual val mcpPlatformModule: Module = module {
    single { McpServerSettings(settings = get()) }
    single { AgentActivityJournal(activity = get()) }

    // Resolved once, here, so a binding the tools need is missing at start-up rather than on the
    // first call of one tool. Everything in it is a use case or a repository the app already had.
    factory {
        McpToolDependencies(
            clock = get(),
            entryRepository = get(),
            transactionRepository = get(),
            accountRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            budgetRepository = get(),
            recurringRepository = get(),
            recurringOccurrenceRepository = get(),
            consolidateMoney = get(),
            calculateBalance = get(),
            calculateCategorySpending = get(),
            calculateCategoryIncome = get(),
            calculateBudgetProgress = get(),
            getPendingRecurring = get(),
            calculateAvailableLimit = get(),
            calculateInvoice = get(),
            calculateReportStats = get(),
        )
    }

    single<McpServerController> {
        DesktopMcpServerController(
            settings = get(),
            journal = get(),
            tools = mcpTools(get()),
        )
    }
}
