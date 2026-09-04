package com.neoutils.finsight.di

import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.mcp.AgentActivityJournal
import com.neoutils.finsight.mcp.DesktopMcpServerController
import com.neoutils.finsight.mcp.McpServerSettings
import com.neoutils.finsight.mcp.McpToolDependencies
import com.neoutils.finsight.mcp.mcpTools
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual val mcpPlatformModule: Module = module {
    // The node behind the `Settings` bound in `:core:common`, which on the JVM is
    // `PreferencesSettings(Preferences.userRoot())`. It is named a second time here because that is
    // the only way to reach it: the wrapper keeps it private and offers no `flush`. Naming it is
    // what lets a choice made in the window be read by the process launched right after it
    // (design D7).
    single { McpServerSettings(settings = get(), store = Preferences.userRoot()) }
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
            installmentRepository = get(),
            budgetRepository = get(),
            recurringRepository = get(),
            recurringOccurrenceRepository = get(),
            baseCurrencyRepository = get(),
            consolidateMoney = get(),
            calculateBalance = get(),
            calculateCategorySpending = get(),
            calculateCategoryIncome = get(),
            calculateBudgetProgress = get(),
            getPendingRecurring = get(),
            calculateAvailableLimit = get(),
            calculateInvoice = get(),
            calculateReportStats = get(),
            registerTransaction = get(),
            updateTransaction = get(),
            deleteTransaction = get(),
            createAccount = get(),
            updateAccount = get(),
            deleteAccount = get(),
            createCategory = get(),
            updateCategory = get(),
            deleteCategory = get(),
            addCreditCard = get(),
            updateCreditCard = get(),
            deleteCreditCard = get(),
            createBudget = get(),
            updateBudget = get(),
            deleteBudget = get(),
            saveRecurring = get(),
            deleteRecurring = get(),
            addInstallment = get(),
            updateInstallment = get(),
            deleteInstallment = get(),
            createInvoice = get(),
            deleteFutureInvoice = get(),
            payInvoicePayment = get(),
            advanceInvoicePayment = get(),
            updateAdvanceInvoicePayment = get(),
            closeInvoice = get(),
            openInvoice = get(),
            reopenInvoice = get(),
            adjustInvoice = get(),
            adjustBalance = get(),
            transferBetweenAccounts = get(),
            updateTransfer = get(),
            setDefaultAccount = get(),
            confirmRecurring = get(),
            skipRecurring = get(),
            archiveAccount = get(),
            unarchiveAccount = get(),
            archiveCreditCard = get(),
            unarchiveCreditCard = get(),
            archiveCategory = get(),
            unarchiveCategory = get(),
            archiveRecurring = get(),
            unarchiveRecurring = get(),
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
