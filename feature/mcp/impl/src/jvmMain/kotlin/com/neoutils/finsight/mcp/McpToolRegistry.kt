package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.tool.GetBalanceTool
import com.neoutils.finsight.mcp.tool.GetBudgetProgressTool
import com.neoutils.finsight.mcp.tool.GetCardOverviewTool
import com.neoutils.finsight.mcp.tool.GetCategoryIncomeTool
import com.neoutils.finsight.mcp.tool.GetCategorySpendingTool
import com.neoutils.finsight.mcp.tool.GetInvoiceTool
import com.neoutils.finsight.mcp.tool.GetMonthSummaryTool
import com.neoutils.finsight.mcp.tool.GetNetWorthTool
import com.neoutils.finsight.mcp.tool.GetPendingRecurringTool
import com.neoutils.finsight.mcp.tool.GetReportStatsTool
import com.neoutils.finsight.mcp.tool.GetSpendingBreakdownTool
import com.neoutils.finsight.mcp.tool.GetTransactionTool
import com.neoutils.finsight.mcp.tool.ListAccountsTool
import com.neoutils.finsight.mcp.tool.ListBudgetsTool
import com.neoutils.finsight.mcp.tool.ListCardsTool
import com.neoutils.finsight.mcp.tool.ListCategoriesTool
import com.neoutils.finsight.mcp.tool.ListInstallmentsTool
import com.neoutils.finsight.mcp.tool.ListInvoicesTool
import com.neoutils.finsight.mcp.tool.ListRecurringTool
import com.neoutils.finsight.mcp.tool.ListTransactionsTool

/**
 * The tools the running server is given — the single place production assembles them.
 *
 * It is one list and not a registration scattered over the tools themselves, because the closure
 * test has to be able to ask "what does this server offer?" and get an answer that is the same one
 * the socket would give. A tool that registered itself from its own file would be reachable by the
 * protocol and invisible to the question.
 *
 * **Adding to this list is half of adding a tool.** The other half is naming it in
 * [McpSurface.offered]; either edit alone fails `McpSurfaceIsClosedTest`, which is what makes a new
 * tool a decision rather than a side effect of writing one.
 *
 * The families are built one at a time, so this list grows in steps. What is here is family 1, the
 * questions — the app calculates and the agent receives the number — and family 2, the catalogue:
 * what exists, what it is called, and the figure that belongs beside it.
 */
internal fun mcpTools(deps: McpToolDependencies): List<McpTool> = listOf(
    GetBalanceTool(
        clock = deps.clock,
        calculateBalance = deps.calculateBalance,
        consolidateMoney = deps.consolidateMoney,
        accountRepository = deps.accountRepository,
    ),
    GetNetWorthTool(
        clock = deps.clock,
        entryRepository = deps.entryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetMonthSummaryTool(
        clock = deps.clock,
        entryRepository = deps.entryRepository,
        categoryRepository = deps.categoryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetCategorySpendingTool(
        clock = deps.clock,
        calculateCategorySpending = deps.calculateCategorySpending,
        entryRepository = deps.entryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetCategoryIncomeTool(
        clock = deps.clock,
        calculateCategoryIncome = deps.calculateCategoryIncome,
        entryRepository = deps.entryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetSpendingBreakdownTool(
        clock = deps.clock,
        calculateCategorySpending = deps.calculateCategorySpending,
        calculateCategoryIncome = deps.calculateCategoryIncome,
        entryRepository = deps.entryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetBudgetProgressTool(
        clock = deps.clock,
        budgetRepository = deps.budgetRepository,
        recurringRepository = deps.recurringRepository,
        transactionRepository = deps.transactionRepository,
        calculateBudgetProgress = deps.calculateBudgetProgress,
    ),
    GetPendingRecurringTool(
        clock = deps.clock,
        recurringRepository = deps.recurringRepository,
        occurrenceRepository = deps.recurringOccurrenceRepository,
        getPendingRecurring = deps.getPendingRecurring,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetCardOverviewTool(
        creditCardRepository = deps.creditCardRepository,
        invoiceRepository = deps.invoiceRepository,
        calculateAvailableLimit = deps.calculateAvailableLimit,
        calculateInvoice = deps.calculateInvoice,
    ),
    GetReportStatsTool(
        clock = deps.clock,
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        calculateReportStats = deps.calculateReportStats,
        consolidateMoney = deps.consolidateMoney,
    ),

    // --- Family 2 — the catalogue: what exists, and the figure beside it --------------------

    ListTransactionsTool(
        clock = deps.clock,
        transactionRepository = deps.transactionRepository,
        entryRepository = deps.entryRepository,
        accountRepository = deps.accountRepository,
        categoryRepository = deps.categoryRepository,
        creditCardRepository = deps.creditCardRepository,
        installmentRepository = deps.installmentRepository,
        consolidateMoney = deps.consolidateMoney,
        baseCurrency = deps.baseCurrency,
    ),
    GetTransactionTool(
        transactionRepository = deps.transactionRepository,
        categoryRepository = deps.categoryRepository,
        installmentRepository = deps.installmentRepository,
        invoiceRepository = deps.invoiceRepository,
    ),
    ListAccountsTool(
        clock = deps.clock,
        accountRepository = deps.accountRepository,
        calculateBalance = deps.calculateBalance,
        consolidateMoney = deps.consolidateMoney,
    ),
    ListCardsTool(
        creditCardRepository = deps.creditCardRepository,
        calculateAvailableLimit = deps.calculateAvailableLimit,
    ),
    ListCategoriesTool(
        clock = deps.clock,
        categoryRepository = deps.categoryRepository,
        entryRepository = deps.entryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    ListInvoicesTool(
        clock = deps.clock,
        invoiceRepository = deps.invoiceRepository,
        creditCardRepository = deps.creditCardRepository,
        entryRepository = deps.entryRepository,
        consolidateMoney = deps.consolidateMoney,
    ),
    GetInvoiceTool(
        clock = deps.clock,
        invoiceRepository = deps.invoiceRepository,
        transactionRepository = deps.transactionRepository,
        entryRepository = deps.entryRepository,
        categoryRepository = deps.categoryRepository,
        installmentRepository = deps.installmentRepository,
    ),
    ListInstallmentsTool(
        installmentRepository = deps.installmentRepository,
        transactionRepository = deps.transactionRepository,
        invoiceRepository = deps.invoiceRepository,
        categoryRepository = deps.categoryRepository,
        creditCardRepository = deps.creditCardRepository,
    ),
    ListBudgetsTool(
        budgetRepository = deps.budgetRepository,
    ),
    ListRecurringTool(
        clock = deps.clock,
        recurringRepository = deps.recurringRepository,
        occurrenceRepository = deps.recurringOccurrenceRepository,
        getPendingRecurring = deps.getPendingRecurring,
    ),
)
