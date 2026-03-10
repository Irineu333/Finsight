package com.neoutils.finsight.ui.screen.report.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.ui.screen.home.AppRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

class ReportViewerViewModel(
    private val route: AppRoute.ReportViewer,
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val calculateReportStatsUseCase: CalculateReportStatsUseCase,
    private val calculateReportCategorySpendingUseCase: CalculateReportCategorySpendingUseCase,
) : ViewModel() {

    private val startDate = LocalDate.parse(route.startDate)
    private val endDate = LocalDate.parse(route.endDate)

    private val perspective: ReportPerspective = when (route.perspectiveType) {
        "CREDIT_CARD" -> ReportPerspective.CreditCardPerspective(
            creditCardId = requireNotNull(route.creditCardId),
        )
        else -> ReportPerspective.AccountPerspective(
            accountIds = route.accountIds,
        )
    }

    val uiState = combine(
        operationRepository.observeAllOperations(),
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
    ) { operations, accounts, creditCards ->
        val stats = calculateReportStatsUseCase(
            operations = operations,
            perspective = perspective,
            startDate = startDate,
            endDate = endDate,
        )

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
            calculateReportCategorySpendingUseCase(
                operations = operations,
                perspective = perspective,
                startDate = startDate,
                endDate = endDate,
            )
        } else null

        val transactionsMap = if (route.includeTransactionList) {
            operations
                .filter { it.date in startDate..endDate }
                .filter { op ->
                    when (perspective) {
                        is ReportPerspective.AccountPerspective -> {
                            op.transactions.any {
                                it.target == com.neoutils.finsight.domain.model.Transaction.Target.ACCOUNT &&
                                    (perspective.accountIds.isEmpty() || it.account?.id in perspective.accountIds)
                            }
                        }
                        is ReportPerspective.CreditCardPerspective -> {
                            op.transactions.any {
                                it.target == com.neoutils.finsight.domain.model.Transaction.Target.CREDIT_CARD &&
                                    it.creditCard?.id == perspective.creditCardId
                            }
                        }
                    }
                }
                .sortedByDescending { it.date }
                .groupBy { it.date }
        } else null

        val perspectiveIconKey = when (perspective) {
            is ReportPerspective.CreditCardPerspective ->
                creditCards.find { it.id == perspective.creditCardId }?.iconKey ?: "card"
            is ReportPerspective.AccountPerspective -> {
                val selected = if (perspective.accountIds.isEmpty()) accounts
                               else accounts.filter { it.id in perspective.accountIds }
                if (selected.size == 1) selected.first().iconKey else "wallet"
            }
        }

        ReportViewerUiState.Content(
            perspectiveLabel = perspectiveLabel,
            perspectiveIconKey = perspectiveIconKey,
            startDate = startDate,
            endDate = endDate,
            initialBalance = stats.initialBalance,
            income = stats.income,
            expense = stats.expense,
            balance = stats.balance,
            categorySpending = categorySpending,
            transactions = transactionsMap,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportViewerUiState.Loading,
    )
}
