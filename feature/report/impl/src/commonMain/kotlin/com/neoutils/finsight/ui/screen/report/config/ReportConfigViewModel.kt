@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.report.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.GenerateReport
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.PerspectiveTab
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ReportConfigViewModel(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val buildReportViewerParams: BuildReportViewerParamsUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val config = MutableStateFlow(ReportConfig.initial())
    private val _events = Channel<ReportConfigEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val invoicesFlow = config
        .map { it.selectedCreditCardId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else invoiceRepository.observeInvoicesByCreditCard(id)
        }

    init {
        selectDefaultAccount()
        autoSelectInvoice()
    }

    val uiState: StateFlow<ReportConfigUiState> = combine(
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        invoicesFlow,
        config,
    ) { accounts, creditCards, invoices, config ->
        ReportConfigUiState.Content(
            config = config,
            accounts = accounts,
            creditCards = creditCards,
            invoices = invoices.reversed(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportConfigUiState.Loading,
    )

    fun onAction(action: ReportConfigAction) = viewModelScope.launch {
        when (action) {
            is ReportConfigAction.SelectPerspective -> {
                config.update { it.copy(selectedTab = action.tab) }
            }

            is ReportConfigAction.ToggleAccount -> {
                config.update { state ->
                    state.copy(
                        selectedAccountIds = buildSet {
                            addAll(state.selectedAccountIds)
                            if (action.accountId in state.selectedAccountIds) {
                                remove(action.accountId)
                            } else {
                                add(action.accountId)
                            }
                        }
                    )
                }
            }

            is ReportConfigAction.SelectCreditCard -> {
                config.update { it.copy(selectedCreditCardId = action.creditCardId) }
            }

            is ReportConfigAction.ToggleInvoice -> {
                config.update { state ->
                    state.copy(
                        selectedInvoiceIds = buildSet {
                            addAll(state.selectedInvoiceIds)
                            if (action.invoiceId in state.selectedInvoiceIds) {
                                remove(action.invoiceId)
                            } else {
                                add(action.invoiceId)
                            }
                        }
                    )
                }
            }

            is ReportConfigAction.SelectStartDate -> {
                config.update { it.copy(startDate = action.date) }
            }

            is ReportConfigAction.SelectEndDate -> {
                config.update { it.copy(endDate = action.date) }
            }

            is ReportConfigAction.ToggleSpendingByCategory -> {
                config.update { it.copy(includeSpendingByCategory = action.enabled) }
            }

            is ReportConfigAction.ToggleIncomeByCategory -> {
                config.update { it.copy(includeIncomeByCategory = action.enabled) }
            }

            is ReportConfigAction.ToggleTransactionList -> {
                config.update { it.copy(includeTransactionList = action.enabled) }
            }

            ReportConfigAction.GenerateReport -> {
                buildReportViewerParams(config.value)?.let { params ->
                    val target = when (params.perspectiveType) {
                        PerspectiveTab.CREDIT_CARD -> "credit_card"
                        PerspectiveTab.ACCOUNT -> "account"
                    }
                    val sections = buildList {
                        if (params.includeSpendingByCategory) add("spending_by_category")
                        if (params.includeIncomeByCategory) add("income_by_category")
                        if (params.includeTransactionList) add("transaction_list")
                    }.joinToString(",")
                    analytics.logEvent(GenerateReport(target, sections))
                    _events.send(ReportConfigEvent.NavigateToViewer(params))
                }
            }
        }
    }

    private fun selectDefaultAccount() = viewModelScope.launch {
        accountRepository.getDefaultAccount()?.let { account ->
            config.update {
                it.copy(selectedAccountIds = setOf(account.id))
            }
        }
    }

    private fun autoSelectInvoice() = viewModelScope.launch {
        invoicesFlow.collectLatest { invoices ->
            val invoiceIds = invoices.map { it.id }.toSet()
            val current = config.value.selectedInvoiceIds
            if (current.none { it in invoiceIds }) {
                val toSelect = invoices.firstOrNull { it.status == Invoice.Status.OPEN }
                    ?: invoices.firstOrNull()
                config.update { it.copy(selectedInvoiceIds = setOfNotNull(toSelect?.id)) }
            }
        }
    }
}
