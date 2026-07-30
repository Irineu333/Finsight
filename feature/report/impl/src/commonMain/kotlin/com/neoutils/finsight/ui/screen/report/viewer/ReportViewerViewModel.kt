@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.ui.model.CategorySpendingUi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.PrintReport
import com.neoutils.finsight.domain.analytics.event.ShareReport
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.sum
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.usecase.ConsolidateFigureUseCase
import com.neoutils.finsight.domain.usecase.consolidationDateOf
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    private val consolidateFigure: ConsolidateFigureUseCase,
    baseCurrencyRepository: IBaseCurrencyRepository,
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
        // Every figure of a report spans its whole scope, so it is consolidated — and the
        // base enters as a flow so the report follows the preference rather than sampling it.
        baseCurrencyRepository.observe(),
    ) { transactions, accounts, creditCards, invoices, facadeLookup, base ->
        // One date for the whole emission: every figure of a report is governed by the same
        // quote, or the summary and the breakdown would explain themselves differently.
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
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
            // A closed invoice is governed by its own closing date, for ever: what the
            // report says about a past period must not move when a quote does.
            val date = consolidationDateOf(invoices.maxOf { it.closingDate }, today)
            suspend fun CurrencyBalance.figure(policy: SignPolicy) =
                consolidateFigure(balance = this, base = base, date = date, policy = policy)
            ReportViewerUiState.Stats.Invoice(
                openingDate = invoices.minOf { it.openingDate },
                closingDate = invoices.maxOf { it.closingDate },
                // The invoice lines follow the same rule as the account lines of this
                // very report: spending subtracts, an advance payment adds, and only the
                // adjustment needs its direction spelled out.
                // Each figure aggregates every invoice in the report, so it spans cards and
                // its currency is not any one card's — which is exactly what consolidation
                // answers for.
                expense = flows.map { it.expense }.sum().figure(SignPolicy.FORCED_NEGATIVE),
                advancePayment = flows.map { it.advancePayment }.sum().figure(SignPolicy.FORCED_POSITIVE),
                adjustment = flows.map { it.adjustment }.sum().figure(SignPolicy.EXPLICIT_SIGN),
                total = owed.sum().figure(SignPolicy.NATURAL),
            )
        } else {
            val scopeStats = calculateReportStatsUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
            )
            val date = consolidationDateOf(endDate, today)
            suspend fun CurrencyBalance.figure(policy: SignPolicy) =
                consolidateFigure(balance = this, base = base, date = date, policy = policy)
            ReportViewerUiState.Stats.Account(
                startDate = startDate,
                endDate = endDate,
                openingBalance = scopeStats.openingBalance.figure(SignPolicy.NATURAL),
                income = scopeStats.income.figure(SignPolicy.FORCED_POSITIVE),
                expense = scopeStats.expense.figure(SignPolicy.FORCED_NEGATIVE),
                balance = scopeStats.balance.figure(SignPolicy.NATURAL),
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
                base = base,
                date = consolidationDateOf(invoices.maxOf { it.closingDate }, today),
                transactionType = TransactionType.EXPENSE,
            )
            else -> calculateReportCategorySpendingUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
                base = base,
                date = consolidationDateOf(endDate, today),
                transactionType = TransactionType.EXPENSE,
            )
        }

        val categoryIncome = when {
            !params.includeIncomeByCategory -> null
            invoices.isNotEmpty() -> calculateReportCategorySpendingUseCase.forDimensions(
                dimensionIds = invoiceDimensionIds.toList(),
                base = base,
                date = consolidationDateOf(invoices.maxOf { it.closingDate }, today),
                transactionType = TransactionType.INCOME,
            )
            else -> calculateReportCategorySpendingUseCase(
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
                base = base,
                date = consolidationDateOf(endDate, today),
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

    /** A category's share, denominated for the surfaces that show it — already consolidated. */
    private fun CategorySpending.toUi() = CategorySpendingUi(
        category = category,
        amount = amount.figure,
        percentage = percentage,
    )
}
