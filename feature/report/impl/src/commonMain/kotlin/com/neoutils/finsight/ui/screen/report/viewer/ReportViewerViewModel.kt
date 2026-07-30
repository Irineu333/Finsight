package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.ASSUMED_SINGLE_CURRENCY
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.ui.model.CategorySpendingUi
import com.neoutils.finsight.extension.MoneyFigure
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.PrintReport
import com.neoutils.finsight.domain.analytics.event.ShareReport
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.sum
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.toTransactionUi
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
        facadeLookupFlow,
    ) { transactions, accounts, creditCards, invoices, facadeLookup ->
        val invoiceIds = invoices.map { it.id }.toSet()
        val invoiceDimensionIds = invoices.mapNotNull { it.dimensionId }.toSet()

        val stats = if (invoices.isNotEmpty()) {
            // Expense / advance-payment / adjustment straight from the ledger — the same
            // `flowsByDimension`/`owedByDimension` the card feature's own invoice screens
            // use, each a single grouped read over every invoice dimension rather than one
            // query per invoice (spec `ledger-reporting`: no alternative that sums entries
            // already loaded).
            val flows = entryRepository.flowsByDimension(invoiceDimensionIds).values
            val owed = entryRepository.owedByDimension(invoiceDimensionIds).values
            val denomination = Denomination.exact(ASSUMED_SINGLE_CURRENCY)
            ReportViewerUiState.Stats.Invoice(
                openingDate = invoices.minOf { it.openingDate },
                closingDate = invoices.maxOf { it.closingDate },
                // The invoice lines follow the same rule as the account lines of this
                // very report: spending subtracts, an advance payment adds, and only the
                // adjustment needs its direction spelled out.
                // Each figure aggregates every invoice in the report, so it spans cards and
                // its currency is not any one card's.
                expense = DisplayAmount.forcedNegative(
                    flows.map { it.expense }.sum()[ASSUMED_SINGLE_CURRENCY],
                    denomination,
                ),
                advancePayment = DisplayAmount.forcedPositive(
                    flows.map { it.advancePayment }.sum()[ASSUMED_SINGLE_CURRENCY],
                    denomination,
                ),
                adjustment = DisplayAmount.explicitSign(
                    flows.map { it.adjustment }.sum()[ASSUMED_SINGLE_CURRENCY],
                    denomination,
                ),
                total = DisplayAmount.natural(owed.sum()[ASSUMED_SINGLE_CURRENCY], denomination),
            )
        } else {
            val scopeStats = calculateReportStatsUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
            )
            val denomination = Denomination.exact(ASSUMED_SINGLE_CURRENCY)
            ReportViewerUiState.Stats.Account(
                startDate = startDate,
                endDate = endDate,
                openingBalance = DisplayAmount.natural(
                    scopeStats.openingBalance[ASSUMED_SINGLE_CURRENCY],
                    denomination,
                ),
                income = DisplayAmount.forcedPositive(scopeStats.income[ASSUMED_SINGLE_CURRENCY], denomination),
                expense = DisplayAmount.forcedNegative(scopeStats.expense[ASSUMED_SINGLE_CURRENCY], denomination),
                balance = DisplayAmount.natural(scopeStats.balance[ASSUMED_SINGLE_CURRENCY], denomination),
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

        // Declared once, here, and consumed by the list and by the export alike: the card's
        // ledger account under a card perspective, nothing under an account one, where
        // several accounts are not a point of view (design D11).
        val perspectiveAccountId = when (perspective) {
            is ReportPerspective.CreditCardPerspective ->
                creditCards.find { it.id == perspective.creditCardId }?.accountId

            is ReportPerspective.AccountPerspective -> null
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
                        it.toTransactionUi(accountId = perspectiveAccountId, lookup = facadeLookup)
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
            categorySpending = categorySpending?.map { it.toUi() },
            categoryIncome = categoryIncome?.map { it.toUi() },
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

    /**
     * A category's share, denominated for the surfaces that show it. The figure spans every
     * account in the report's scope, so reducing it is consolidation's job (task 8.2); until
     * then the report has a single currency to state it in.
     */
    private fun CategorySpending.toUi() = CategorySpendingUi(
        category = category,
        amount = MoneyFigure.of(
            DisplayAmount.natural(amount, Denomination.exact(ASSUMED_SINGLE_CURRENCY))
        ),
        percentage = percentage,
    )
}
