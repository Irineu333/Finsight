package com.neoutils.finsight.mcp

import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import kotlin.time.Clock

/**
 * Everything the tools read the app through, gathered in one place.
 *
 * It exists so that [mcpTools] is a **list and nothing else** — no `get()` scattered through it, and
 * no tool reaching for a collaborator of its own. Two consequences, both of them the point:
 *
 * - **The graph closes in one place.** Koin resolves this once, at start-up, and a binding that is
 *   missing fails there instead of on the first call of one tool.
 * - **A test assembles the same registry the desktop does.** The closure test and the protocol tests
 *   build one of these and get the production list, rather than a list of their own that would drift
 *   away from it silently.
 *
 * Every member is a **use case or a repository the app already had**. Nothing new is declared here,
 * and nothing here decides anything: a tool composes what these answer, and where the answer is a
 * rule, the rule stayed with whoever already owned it.
 */
internal class McpToolDependencies(
    val clock: Clock,
    val entryRepository: IEntryRepository,
    val transactionRepository: ITransactionRepository,
    val accountRepository: IAccountRepository,
    val categoryRepository: ICategoryRepository,
    val creditCardRepository: ICreditCardRepository,
    val invoiceRepository: IInvoiceRepository,
    val installmentRepository: IInstallmentRepository,
    val budgetRepository: IBudgetRepository,
    val recurringRepository: IRecurringRepository,
    val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    baseCurrencyRepository: IBaseCurrencyRepository,
    val consolidateMoney: ConsolidateMoneyUseCase,
    val calculateBalance: CalculateBalanceUseCase,
    val calculateCategorySpending: CalculateCategorySpendingUseCase,
    val calculateCategoryIncome: CalculateCategoryIncomeUseCase,
    val calculateBudgetProgress: CalculateBudgetProgressUseCase,
    val getPendingRecurring: GetPendingRecurringUseCase,
    val calculateAvailableLimit: CalculateAvailableLimitUseCase,
    val calculateInvoice: CalculateInvoiceUseCase,
    val calculateReportStats: CalculateReportStatsUseCase,
) {

    /**
     * **The tie-break between the two ends of a cross-currency operation** — the one thing this
     * surface reads the base currency for, and the reason it takes the repository at all.
     *
     * Nothing is denominated by it and nothing is converted. An operation that crossed currencies
     * has *two* exact figures, both the ledger's own — US$ 550,00 left and R$ 500,00 of an invoice
     * was paid — and a listing with no account named has to state one of them. Which one is
     * `Transaction.figureLegUnder`'s decision, and it is the same one the app's own neutral lists
     * consume, so a posting cannot read as one figure on the screen and another to the agent. Where
     * neither end is in the base the reading is unchanged: the base is never a fallback.
     *
     * A function rather than a value because the tools are assembled once, at start-up, and the
     * preference outlives that: a list read after the user changes it must tie-break by the new one.
     */
    val baseCurrency: () -> String = { baseCurrencyRepository.observe().value }
}
