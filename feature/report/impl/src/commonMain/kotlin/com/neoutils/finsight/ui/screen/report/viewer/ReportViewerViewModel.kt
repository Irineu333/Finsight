package com.neoutils.finsight.ui.screen.report.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.PrintReport
import com.neoutils.finsight.domain.analytics.event.ShareReport
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.screen.report.render.ReportDocumentRenderer
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.report_viewer_badge_account
import com.neoutils.finsight.resources.report_viewer_badge_credit_card
import com.neoutils.finsight.ui.screen.report.ReportViewerParams
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportViewerViewModel(
    private val params: ReportViewerParams,
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val calculateReportStatsUseCase: CalculateReportStatsUseCase,
    private val calculateReportCategorySpendingUseCase: CalculateReportCategorySpendingUseCase,
    private val entryRepository: IEntryRepository,
    private val renderer: ReportDocumentRenderer,
    private val analytics: Analytics,
) : ViewModel() {

    private val startDate = params.startDate
    private val endDate = params.endDate

    private val perspective: ReportPerspective = when (params.perspectiveType) {
        PerspectiveTab.CREDIT_CARD -> ReportPerspective.CreditCardPerspective(
            creditCardId = requireNotNull(params.creditCardId),
        )

        PerspectiveTab.ACCOUNT -> ReportPerspective.AccountPerspective(
            accountIds = params.accountIds,
        )
    }

    private val invoicesFlow = when {
        params.invoiceIds.isEmpty() -> flowOf(emptyList())
        else -> invoiceRepository.observeInvoicesByCreditCard(
            requireNotNull(params.creditCardId)
        ).map { invoices -> invoices.filter { it.id in params.invoiceIds } }
    }

    private val _events = Channel<ReportViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // The rows still show a category icon and an installment badge; the ledger hands
    // out only the identities behind them (design D6).
    private val facadeLookupFlow = combine(
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
    ) { categories, installments -> TransactionFacadeLookup.of(categories, installments) }

    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        invoicesFlow,
        facadeLookupFlow,
    ) { transactions, accounts, creditCards, invoices, facadeLookup ->
        val invoiceIds = invoices.map { it.id }.toSet()
        val invoiceDimensionIds = invoices.mapNotNull { it.dimensionId }.toSet()

        val stats = if (invoices.isNotEmpty()) {
            // Expense / advance-payment / adjustment straight from the ledger, one read
            // per invoice dimension — the same `dimensionFlows` the card feature's own
            // invoice screens use, instead of re-summing the loaded legs in memory
            // (spec `ledger-reporting`: no alternative that sums entries already loaded).
            val flows = invoiceDimensionIds.map { entryRepository.dimensionFlows(it) }
            ReportViewerUiState.Stats.Invoice(
                openingDate = invoices.minOf { it.openingDate },
                closingDate = invoices.maxOf { it.closingDate },
                expense = flows.sumOf { it.expense },
                advancePayment = flows.sumOf { it.advancePayment },
                adjustment = flows.sumOf { it.adjustment },
                total = invoiceDimensionIds.sumOf { entryRepository.dimensionOwed(it) },
            )
        } else {
            val scopeStats = calculateReportStatsUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
            )
            ReportViewerUiState.Stats.Account(
                startDate = startDate,
                endDate = endDate,
                openingBalance = scopeStats.openingBalance,
                income = scopeStats.income,
                expense = scopeStats.expense,
                balance = scopeStats.balance,
            )
        }

        val perspectiveLabel = when (perspective) {
            is ReportPerspective.AccountPerspective -> {
                accounts
                    .filter { it.id in perspective.accountIds }
                    .joinToString(", ") { it.name }
                    .takeIf { it.isNotBlank() } ?: accounts.joinToString(", ") { it.name }
            }

            is ReportPerspective.CreditCardPerspective -> {
                creditCards.find { it.id == perspective.creditCardId }?.name ?: ""
            }
        }

        val categorySpending = when {
            !params.includeSpendingByCategory -> null
            invoices.isNotEmpty() -> calculateReportCategorySpendingUseCase.forDimensions(
                dimensionIds = invoiceDimensionIds.toList(),
                transactionType = TransactionType.EXPENSE,
            )
            else -> calculateReportCategorySpendingUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
                transactionType = TransactionType.EXPENSE,
            )
        }

        val categoryIncome = when {
            !params.includeIncomeByCategory -> null
            invoices.isNotEmpty() -> calculateReportCategorySpendingUseCase.forDimensions(
                dimensionIds = invoiceDimensionIds.toList(),
                transactionType = TransactionType.INCOME,
            )
            else -> calculateReportCategorySpendingUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
                transactionType = TransactionType.INCOME,
            )
        }

        val transactionsMap = if (params.includeTransactionList) {
            val filteredOps = if (invoices.isNotEmpty()) {
                // One test, not two: an invoice *is* the dimension its card leg
                // carries, so the id comparison the first clause used to make is the
                // same question asked of the facade (design D6).
                transactions.filter { op ->
                    op.entries.any { it.dimensionId in invoiceDimensionIds }
                }
            } else {
                transactions
                    .filter { it.date in startDate..endDate }
                    .filter { op ->
                        when (perspective) {
                            is ReportPerspective.AccountPerspective -> {
                                op.entries.any {
                                    it.account.type == AccountType.ASSET &&
                                            (perspective.accountIds.isEmpty() || it.account.id in perspective.accountIds)
                                }
                            }

                            is ReportPerspective.CreditCardPerspective -> {
                                val liabilityAccountId = creditCards
                                    .find { it.id == perspective.creditCardId }?.accountId
                                op.entries.any {
                                    it.account.type == AccountType.LIABILITY &&
                                            it.account.id == liabilityAccountId
                                }
                            }
                        }
                    }
            }
            filteredOps.sortedByDescending { it.date }.groupBy { it.date }
        } else null

        val perspectiveIconKey = when (perspective) {
            is ReportPerspective.CreditCardPerspective -> {
                creditCards.find { it.id == perspective.creditCardId }?.iconKey ?: "card"
            }

            is ReportPerspective.AccountPerspective -> {
                val selected = if (perspective.accountIds.isEmpty()) accounts
                else accounts.filter { it.id in perspective.accountIds }
                if (selected.size == 1) selected.first().iconKey else "wallet"
            }
        }

        val perspectiveBadge = when (perspective) {
            is ReportPerspective.CreditCardPerspective -> UiText.Res(Res.string.report_viewer_badge_credit_card)
            is ReportPerspective.AccountPerspective -> UiText.Res(Res.string.report_viewer_badge_account)
        }

        ReportViewerUiState.Content(
            perspectiveLabel = perspectiveLabel,
            perspectiveBadge = perspectiveBadge,
            perspectiveIconKey = perspectiveIconKey,
            stats = stats,
            categorySpending = categorySpending,
            categoryIncome = categoryIncome,
            transactions = transactionsMap,
            facadeLookup = facadeLookup,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportViewerUiState.Loading,
    )

    fun onAction(action: ReportViewerAction) = viewModelScope.launch {
        when (action) {
            is ReportViewerAction.Share -> {
                _events.send(ReportViewerEvent.Share(renderer.render(action.layout)))
                analytics.logEvent(ShareReport)
            }

            is ReportViewerAction.Print -> {
                _events.send(ReportViewerEvent.Print(renderer.render(action.layout)))
                analytics.logEvent(PrintReport)
            }
        }
    }
}
