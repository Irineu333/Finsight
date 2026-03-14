package com.neoutils.finsight.ui.screen.report.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.report.ReportDocumentRenderer
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.report_viewer_badge_account
import com.neoutils.finsight.resources.report_viewer_badge_credit_card
import com.neoutils.finsight.ui.screen.home.AppRoute
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ReportViewerViewModel(
    private val route: AppRoute.ReportViewer,
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateReportStatsUseCase: CalculateReportStatsUseCase,
    private val calculateReportCategorySpendingUseCase: CalculateReportCategorySpendingUseCase,
    private val renderer: ReportDocumentRenderer,
) : ViewModel() {

    private val startDate = LocalDate.parse(route.startDate)
    private val endDate = LocalDate.parse(route.endDate)

    private val perspective: ReportPerspective = when (route.perspectiveType) {
        PerspectiveTab.CREDIT_CARD -> ReportPerspective.CreditCardPerspective(
            creditCardId = requireNotNull(route.creditCardId),
        )

        PerspectiveTab.ACCOUNT -> ReportPerspective.AccountPerspective(
            accountIds = route.accountIds,
        )
    }

    private val invoiceFlow = when (val id = route.invoiceId) {
        null -> flowOf(null)
        else -> invoiceRepository.observeInvoiceById(id)
    }

    private val _events = Channel<ReportViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        operationRepository.observeAllOperations(),
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        invoiceFlow,
    ) { operations, accounts, creditCards, invoice ->

        val stats = if (invoice != null) {
            val invoiceTransactions = operations
                .flatMap { it.transactions }
                .filter { it.invoice?.id == invoice.id && it.target == Transaction.Target.CREDIT_CARD }

            val expense = invoiceTransactions
                .filter { it.type == Transaction.Type.EXPENSE }
                .sumOf { it.amount }
            val advancePayment = invoiceTransactions
                .filter { it.type == Transaction.Type.INCOME && it.isInvoicePayment }
                .sumOf { it.amount }
            val adjustment = invoiceTransactions
                .filter { it.type == Transaction.Type.ADJUSTMENT }
                .sumOf { it.amount }
            val total = invoiceTransactions.sumOf { -it.signedImpact() }

            ReportViewerUiState.Stats.Invoice(invoice, expense, advancePayment, adjustment, total)
        } else {
            val reportStats = calculateReportStatsUseCase(
                operations = operations,
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
            )
            ReportViewerUiState.Stats.Account(
                startDate = startDate,
                endDate = endDate,
                initialBalance = reportStats.initialBalance,
                income = reportStats.income,
                expense = reportStats.expense,
                balance = reportStats.balance,
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

        val categorySpending = if (route.includeSpendingByCategory) {
            if (invoice != null) {
                val invoiceTransactions = operations
                    .flatMap { it.transactions }
                    .filter { it.invoice?.id == invoice.id && it.target == Transaction.Target.CREDIT_CARD }
                val expenseTransactions = invoiceTransactions.filter { it.type == Transaction.Type.EXPENSE }
                val totalExpense = expenseTransactions.sumOf { it.amount }
                if (totalExpense > 0) {
                    expenseTransactions
                        .groupBy { it.category }
                        .mapNotNull { (category, txs) ->
                            val cat = category ?: return@mapNotNull null
                            val amount = txs.sumOf { it.amount }
                            CategorySpending(cat, amount, (amount / totalExpense) * 100)
                        }
                        .sortedByDescending { it.amount }
                } else {
                    emptyList()
                }
            } else {
                calculateReportCategorySpendingUseCase(
                    operations = operations,
                    perspective = perspective,
                    startDate = startDate,
                    endDate = endDate,
                )
            }
        } else null

        val transactionsMap = if (route.includeTransactionList) {
            val filteredOps = if (invoice != null) {
                operations.filter { op ->
                    op.targetInvoice?.id == invoice.id ||
                            op.transactions.any { tx -> tx.invoice?.id == invoice.id }
                }
            } else {
                operations
                    .filter { it.date in startDate..endDate }
                    .filter { op ->
                        when (perspective) {
                            is ReportPerspective.AccountPerspective -> {
                                op.transactions.any {
                                    it.target == Transaction.Target.ACCOUNT &&
                                            (perspective.accountIds.isEmpty() || it.account?.id in perspective.accountIds)
                                }
                            }

                            is ReportPerspective.CreditCardPerspective -> {
                                op.transactions.any {
                                    it.target == Transaction.Target.CREDIT_CARD &&
                                            it.creditCard?.id == perspective.creditCardId
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
            transactions = transactionsMap,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportViewerUiState.Loading,
    )

    fun onAction(action: ReportViewerAction) = viewModelScope.launch {
        when (action) {
            is ReportViewerAction.ShareAsHtml -> {
                _events.send(
                    ReportViewerEvent.Share(renderer.render(action.layout))
                )
            }

            is ReportViewerAction.Print -> {
                _events.send(
                    ReportViewerEvent.Print(renderer.render(action.layout))
                )
            }
        }
    }
}
