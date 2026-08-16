package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.tool.AdjustBalanceTool
import com.neoutils.finsight.mcp.tool.AdjustInvoiceTool
import com.neoutils.finsight.mcp.tool.AdvanceInvoicePaymentTool
import com.neoutils.finsight.mcp.tool.ArchiveEntityTool
import com.neoutils.finsight.mcp.tool.CloseInvoiceTool
import com.neoutils.finsight.mcp.tool.ConfirmRecurringTool
import com.neoutils.finsight.mcp.tool.CreateAccountTool
import com.neoutils.finsight.mcp.tool.CreateBudgetTool
import com.neoutils.finsight.mcp.tool.CreateCardTool
import com.neoutils.finsight.mcp.tool.CreateCategoryTool
import com.neoutils.finsight.mcp.tool.CreateInstallmentTool
import com.neoutils.finsight.mcp.tool.CreateInvoiceTool
import com.neoutils.finsight.mcp.tool.CreateRecurringTool
import com.neoutils.finsight.mcp.tool.CreateTransactionTool
import com.neoutils.finsight.mcp.tool.DeleteAccountTool
import com.neoutils.finsight.mcp.tool.DeleteBudgetTool
import com.neoutils.finsight.mcp.tool.DeleteCardTool
import com.neoutils.finsight.mcp.tool.DeleteCategoryTool
import com.neoutils.finsight.mcp.tool.DeleteInstallmentTool
import com.neoutils.finsight.mcp.tool.DeleteInvoiceTool
import com.neoutils.finsight.mcp.tool.DeleteRecurringTool
import com.neoutils.finsight.mcp.tool.DeleteTransactionTool
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
import com.neoutils.finsight.mcp.tool.OpenInvoiceTool
import com.neoutils.finsight.mcp.tool.PayInvoiceTool
import com.neoutils.finsight.mcp.tool.ReopenInvoiceTool
import com.neoutils.finsight.mcp.tool.SetDefaultAccountTool
import com.neoutils.finsight.mcp.tool.SkipRecurringTool
import com.neoutils.finsight.mcp.tool.TransferTool
import com.neoutils.finsight.mcp.tool.UnarchiveEntityTool
import com.neoutils.finsight.mcp.tool.UpdateAccountTool
import com.neoutils.finsight.mcp.tool.UpdateBudgetTool
import com.neoutils.finsight.mcp.tool.UpdateCardTool
import com.neoutils.finsight.mcp.tool.UpdateCategoryTool
import com.neoutils.finsight.mcp.tool.UpdateInstallmentTool
import com.neoutils.finsight.mcp.tool.UpdateRecurringTool
import com.neoutils.finsight.mcp.tool.UpdateTransactionTool

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
 * All four families are here. Family 1, the questions — the app calculates and the agent receives
 * the number. Family 2, the catalogue: what exists, what it is called, and the figure that belongs
 * beside it. Family 3, the registration: what is created, altered and removed. And family 4, the
 * operations: what moves money or moves something through its life cycle. Every one of them reaches
 * the domain through the use case that already owns the rule.
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

    // --- Family 3 — the registration: what is created, altered and removed -----------------
    //
    // Split over two permission axes and not two families: creating and altering are one grant,
    // and every `delete_*` below is on the other. What a user is asked is *what an agent may do*,
    // and removing permanently is a different question from writing.

    CreateTransactionTool(
        clock = deps.clock,
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        installmentRepository = deps.installmentRepository,
        invoiceRepository = deps.invoiceRepository,
        registerTransaction = deps.registerTransaction,
    ),
    UpdateTransactionTool(
        transactionRepository = deps.transactionRepository,
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        installmentRepository = deps.installmentRepository,
        invoiceRepository = deps.invoiceRepository,
        updateTransaction = deps.updateTransaction,
    ),
    DeleteTransactionTool(
        transactionRepository = deps.transactionRepository,
        deleteTransaction = deps.deleteTransaction,
    ),
    CreateAccountTool(
        accountRepository = deps.accountRepository,
        createAccount = deps.createAccount,
    ),
    UpdateAccountTool(
        accountRepository = deps.accountRepository,
        updateAccount = deps.updateAccount,
    ),
    DeleteAccountTool(
        accountRepository = deps.accountRepository,
        deleteAccount = deps.deleteAccount,
    ),
    CreateCardTool(
        addCreditCard = deps.addCreditCard,
    ),
    UpdateCardTool(
        creditCardRepository = deps.creditCardRepository,
        updateCreditCard = deps.updateCreditCard,
    ),
    DeleteCardTool(
        creditCardRepository = deps.creditCardRepository,
        deleteCreditCard = deps.deleteCreditCard,
    ),
    CreateCategoryTool(
        createCategory = deps.createCategory,
    ),
    UpdateCategoryTool(
        categoryRepository = deps.categoryRepository,
        updateCategory = deps.updateCategory,
    ),
    DeleteCategoryTool(
        categoryRepository = deps.categoryRepository,
        deleteCategory = deps.deleteCategory,
    ),
    CreateBudgetTool(
        categoryRepository = deps.categoryRepository,
        recurringRepository = deps.recurringRepository,
        createBudget = deps.createBudget,
    ),
    UpdateBudgetTool(
        budgetRepository = deps.budgetRepository,
        categoryRepository = deps.categoryRepository,
        recurringRepository = deps.recurringRepository,
        updateBudget = deps.updateBudget,
    ),
    DeleteBudgetTool(
        budgetRepository = deps.budgetRepository,
        deleteBudget = deps.deleteBudget,
    ),
    CreateRecurringTool(
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        saveRecurring = deps.saveRecurring,
    ),
    UpdateRecurringTool(
        recurringRepository = deps.recurringRepository,
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        saveRecurring = deps.saveRecurring,
    ),
    DeleteRecurringTool(
        recurringRepository = deps.recurringRepository,
        deleteRecurring = deps.deleteRecurring,
    ),
    CreateInstallmentTool(
        clock = deps.clock,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        installmentRepository = deps.installmentRepository,
        invoiceRepository = deps.invoiceRepository,
        addInstallment = deps.addInstallment,
    ),
    UpdateInstallmentTool(
        installmentRepository = deps.installmentRepository,
        updateInstallment = deps.updateInstallment,
    ),
    DeleteInstallmentTool(
        installmentRepository = deps.installmentRepository,
        deleteInstallment = deps.deleteInstallment,
    ),
    CreateInvoiceTool(
        creditCardRepository = deps.creditCardRepository,
        createInvoice = deps.createInvoice,
    ),
    DeleteInvoiceTool(
        invoiceRepository = deps.invoiceRepository,
        deleteFutureInvoice = deps.deleteFutureInvoice,
    ),

    // --- Family 4 — the operations: what moves money or moves a life cycle -----------------
    //
    // One axis, thirteen tools, and one of them is the reason the surface has a rule about
    // where a decision lives: `pay_invoice` posts the payment and settles the invoice
    // together, through the use case that does both.

    PayInvoiceTool(
        clock = deps.clock,
        invoiceRepository = deps.invoiceRepository,
        accountRepository = deps.accountRepository,
        calculateInvoice = deps.calculateInvoice,
        payInvoicePayment = deps.payInvoicePayment,
    ),
    AdvanceInvoicePaymentTool(
        clock = deps.clock,
        invoiceRepository = deps.invoiceRepository,
        accountRepository = deps.accountRepository,
        calculateInvoice = deps.calculateInvoice,
        advanceInvoicePayment = deps.advanceInvoicePayment,
    ),
    CloseInvoiceTool(
        clock = deps.clock,
        invoiceRepository = deps.invoiceRepository,
        calculateInvoice = deps.calculateInvoice,
        closeInvoice = deps.closeInvoice,
    ),
    OpenInvoiceTool(
        creditCardRepository = deps.creditCardRepository,
        invoiceRepository = deps.invoiceRepository,
        calculateInvoice = deps.calculateInvoice,
        openInvoice = deps.openInvoice,
    ),
    ReopenInvoiceTool(
        invoiceRepository = deps.invoiceRepository,
        calculateInvoice = deps.calculateInvoice,
        reopenInvoice = deps.reopenInvoice,
    ),
    AdjustInvoiceTool(
        clock = deps.clock,
        invoiceRepository = deps.invoiceRepository,
        calculateInvoice = deps.calculateInvoice,
        adjustInvoice = deps.adjustInvoice,
    ),
    AdjustBalanceTool(
        clock = deps.clock,
        accountRepository = deps.accountRepository,
        calculateBalance = deps.calculateBalance,
        adjustBalance = deps.adjustBalance,
    ),
    TransferTool(
        clock = deps.clock,
        accountRepository = deps.accountRepository,
        transferBetweenAccounts = deps.transferBetweenAccounts,
    ),
    SetDefaultAccountTool(
        accountRepository = deps.accountRepository,
        setDefaultAccount = deps.setDefaultAccount,
    ),
    ConfirmRecurringTool(
        clock = deps.clock,
        recurringRepository = deps.recurringRepository,
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        invoiceRepository = deps.invoiceRepository,
        confirmRecurring = deps.confirmRecurring,
    ),
    SkipRecurringTool(
        clock = deps.clock,
        recurringRepository = deps.recurringRepository,
        skipRecurring = deps.skipRecurring,
    ),
    ArchiveEntityTool(
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        recurringRepository = deps.recurringRepository,
        archiveAccount = deps.archiveAccount,
        archiveCreditCard = deps.archiveCreditCard,
        archiveCategory = deps.archiveCategory,
        archiveRecurring = deps.archiveRecurring,
    ),
    UnarchiveEntityTool(
        accountRepository = deps.accountRepository,
        creditCardRepository = deps.creditCardRepository,
        categoryRepository = deps.categoryRepository,
        recurringRepository = deps.recurringRepository,
        unarchiveAccount = deps.unarchiveAccount,
        unarchiveCreditCard = deps.unarchiveCreditCard,
        unarchiveCategory = deps.unarchiveCategory,
        unarchiveRecurring = deps.unarchiveRecurring,
    ),
)
