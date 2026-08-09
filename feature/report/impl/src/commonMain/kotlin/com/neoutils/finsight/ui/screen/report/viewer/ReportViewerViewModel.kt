package com.neoutils.finsight.ui.screen.report.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.PrintReport
import com.neoutils.finsight.domain.analytics.event.ShareReport
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
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
    private val consolidateMoney: ConsolidateMoneyUseCase,
    private val observeConsolidationChanges: ObserveConsolidationChangesUseCase,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
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

    /**
     * The facades, plus the signal that something under a consolidated figure moved.
     * Fused here rather than added to the combine below for the same reason the facades
     * themselves are: that combine is at the arity ceiling. A rate writes no entry, so
     * the ledger's own trigger does not carry it.
     */
    private val facadesAndConsolidation = combine(
        facadeLookupFlow,
        observeConsolidationChanges(),
    ) { lookup, _ -> lookup }

    // A report may be scoped to an account or card that has since been archived (the
    // config picker offers closed ones too), so the viewer resolves the perspective
    // label, icon and — for cards — the LIABILITY account id from the *including
    // closed* facade. The active facade would drop an archived scope, blanking the
    // label/icon and emptying the transaction list. Mirrors the category flow above.
    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        accountRepository.observeAllAccountsIncludingClosed(),
        creditCardRepository.observeAllCreditCardsIncludingClosed(),
        invoicesFlow,
        facadesAndConsolidation,
    ) { transactions, accounts, creditCards, invoices, facadeLookup ->
        val invoiceIds = invoices.map { it.id }.toSet()
        val invoiceDimensionIds = invoices.mapNotNull { it.dimensionId }.toSet()

        // Declared once, here, and consumed by the stats, by the list and by the export
        // alike: the card's ledger account under a card perspective, nothing under an
        // account one, where several accounts are not a point of view (design D11).
        val perspectiveAccountId = when (perspective) {
            is ReportPerspective.CreditCardPerspective ->
                creditCards.find { it.id == perspective.creditCardId }?.accountId

            is ReportPerspective.AccountPerspective -> null
        }

        val stats = if (invoices.isNotEmpty()) {
            // Expense / advance-payment / adjustment straight from the ledger — the same
            // `flowsByDimension`/`owedByDimension` the card feature's own invoice screens
            // use, each a single grouped read over every invoice dimension rather than one
            // query per invoice (spec `ledger-reporting`: no alternative that sums entries
            // already loaded).
            val flows = entryRepository.flowsByDimensionByCurrency(invoiceDimensionIds).values
            val owed = entryRepository.owedByDimensionByCurrency(invoiceDimensionIds).values
            // Every one of these figures belongs to a single facade — the card whose
            // invoices this report is about — so it is denominated by the LIABILITY
            // account the card projects onto and by nothing else, base included
            // (design D17, D29). Nothing was converted to get here, so it is exact.
            val cardCurrency = requireNotNull(
                perspectiveAccountId?.let { accountRepository.getAccountById(it)?.currency }
            ) { "an invoice report is scoped to a card, and a card has a ledger account" }
            ReportViewerUiState.Stats.Invoice(
                openingDate = invoices.minOf { it.openingDate },
                closingDate = invoices.maxOf { it.closingDate },
                // The invoice lines follow the same rule as the account lines of this
                // very report: spending subtracts, an advance payment adds, and only the
                // adjustment needs its direction spelled out.
                // Every invoice here belongs to the one card the report is about, so
                // every figure is in `cardCurrency` and the sum of them is exact —
                // reduced at each invoice by the facade's own guarantee, never by the
                // ledger presuming it (design D8).
                expense = DisplayAmount.forcedNegative(
                    flows.sumOf { it.expense.only(cardCurrency) }, cardCurrency, isApproximate = false,
                ),
                advancePayment = DisplayAmount.forcedPositive(
                    flows.sumOf { it.advancePayment.only(cardCurrency) }, cardCurrency, isApproximate = false,
                ),
                adjustment = DisplayAmount.explicitSign(
                    flows.sumOf { it.adjustment.only(cardCurrency) }, cardCurrency, isApproximate = false,
                ),
                total = DisplayAmount.natural(
                    owed.sumOf { it.only(cardCurrency) }, cardCurrency, isApproximate = false,
                ),
            )
        } else {
            val scopeStats = calculateReportStatsUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
            )
            // A scope spans accounts, and accounts may differ in currency, so these four
            // are consolidated figures by nature: the reducer denominates them, at the
            // rates of the day the period ends — a report about March must not move when
            // a rate changes in April. The base reaches the screen only through the
            // reducer's mouth (design D9, D29).
            ReportViewerUiState.Stats.Account(
                startDate = startDate,
                endDate = endDate,
                openingBalance = consolidateMoney(
                    scopeStats.openingBalance,
                    on = endDate,
                    policy = DisplayAmount::natural,
                ),
                income = consolidateMoney(
                    scopeStats.income,
                    on = endDate,
                    policy = DisplayAmount::forcedPositive,
                ),
                expense = consolidateMoney(
                    scopeStats.expense,
                    on = endDate,
                    policy = DisplayAmount::forcedNegative,
                ),
                balance = consolidateMoney(
                    scopeStats.balance,
                    on = endDate,
                    policy = DisplayAmount::natural,
                ),
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
                on = endDate,
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
                on = endDate,
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
                                op.entries.any {
                                    it.account.type == AccountType.LIABILITY &&
                                            it.account.id == perspectiveAccountId
                                }
                            }
                        }
                    }
            }
            // Mapped here rather than in the screen and again in the export (design D12):
            // one list, one perspective, two consumers that cannot disagree.
            filteredOps
                .sortedByDescending { it.date }
                .groupBy { it.date }
                .mapValues { (_, ops) ->
                    ops.mapNotNull {
                        it.toTransactionUi(
                            accountId = perspectiveAccountId,
                            lookup = facadeLookup,
                            // Only reaches a line whose report has no account
                            // perspective: with one, that account's currency is the
                            // line's, whatever the base.
                            baseCurrency = baseCurrencyRepository.observe().value,
                        )
                    }
                }
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

/**
 * The one term of a figure the card facade guarantees is mono-currency.
 *
 * Stated as a fallback to zero rather than an assertion: a broken guarantee is a bug
 * elsewhere, and a report is not the place to crash over it.
 */
private fun com.neoutils.finsight.domain.model.MoneyByCurrency.only(currency: String): Double =
    this[currency] ?: 0.0
