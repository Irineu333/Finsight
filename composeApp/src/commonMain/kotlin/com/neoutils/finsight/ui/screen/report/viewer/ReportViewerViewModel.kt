package com.neoutils.finsight.ui.screen.report.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.report.HtmlReportDocumentRenderer
import com.neoutils.finsight.report.ReportLayout
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportOutputResult
import com.neoutils.finsight.report.ReportOutputService
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.report_output_error_generic
import com.neoutils.finsight.resources.report_output_error_printing_unsupported
import com.neoutils.finsight.resources.report_output_error_unsupported_format
import com.neoutils.finsight.resources.report_output_export_success
import com.neoutils.finsight.resources.report_output_export_success_with_location
import com.neoutils.finsight.resources.report_output_print_queued
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.resources.report_viewer_badge_account
import com.neoutils.finsight.resources.report_viewer_badge_credit_card
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.ui.screen.home.AppRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ReportViewerViewModel(
    private val route: AppRoute.ReportViewer,
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val calculateReportStatsUseCase: CalculateReportStatsUseCase,
    private val calculateReportCategorySpendingUseCase: CalculateReportCategorySpendingUseCase,
    private val reportRenderer: HtmlReportDocumentRenderer,
    private val reportOutputService: ReportOutputService,
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

    private val _event = MutableSharedFlow<UiText>()
    val event = _event.asSharedFlow()

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

        val perspectiveBadge = when (perspective) {
            is ReportPerspective.CreditCardPerspective -> UiText.Res(Res.string.report_viewer_badge_credit_card)
            is ReportPerspective.AccountPerspective -> UiText.Res(Res.string.report_viewer_badge_account)
        }

        ReportViewerUiState.Content(
            perspectiveLabel = perspectiveLabel,
            perspectiveBadge = perspectiveBadge,
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

    fun exportAsHtml(layout: ReportLayout) = viewModelScope.launch {
        val document = reportRenderer.render(layout)
        val outputResult = reportOutputService.export(document)
        emitOutputResult(
            outputResult = outputResult,
            success = when (outputResult) {
                is ReportOutputResult.Success -> outputResult.location?.let {
                    UiText.ResWithArgs(Res.string.report_output_export_success_with_location, it)
                } ?: UiText.Res(Res.string.report_output_export_success)
                is ReportOutputResult.Failure -> null
            },
        )
    }

    fun print(layout: ReportLayout) = viewModelScope.launch {
        val document = reportRenderer.render(layout)
        val outputResult = reportOutputService.print(document)
        emitOutputResult(
            outputResult = outputResult,
            success = UiText.Res(Res.string.report_output_print_queued),
        )
    }

    private suspend fun emitOutputResult(
        outputResult: ReportOutputResult,
        success: UiText?,
    ) {
        when (outputResult) {
            is ReportOutputResult.Success -> {
                success?.let { _event.emit(it) }
            }

            is ReportOutputResult.Failure -> {
                _event.emit(outputResult.error.toUiText())
            }
        }
    }
}

private fun ReportOutputError.toUiText(): UiText {
    return when (this) {
        ReportOutputError.UnsupportedFormat -> UiText.Res(Res.string.report_output_error_unsupported_format)
        ReportOutputError.UnsupportedPrinting -> UiText.Res(Res.string.report_output_error_printing_unsupported)
        ReportOutputError.IoError -> UiText.Res(Res.string.report_output_error_generic)
        ReportOutputError.Unknown -> UiText.Res(Res.string.report_output_error_generic)
    }
}
