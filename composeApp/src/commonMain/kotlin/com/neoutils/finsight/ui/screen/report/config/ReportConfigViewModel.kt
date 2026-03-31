@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.report.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.ui.screen.report.ReportRoute
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReportConfigViewModel(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
) : ViewModel() {

    private val initialConfig = ReportConfigUiState.initial()
    private val config = MutableStateFlow(initialConfig)

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

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        invoicesFlow,
        config,
    ) { accounts, creditCards, invoices, config ->
        config.copy(
            accounts = accounts,
            creditCards = creditCards,
            invoices = invoices.reversed(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initialConfig,
    )

    fun onAction(action: ReportConfigAction) = viewModelScope.launch {
        when (action) {
            is ReportConfigAction.SelectPerspective -> {
                config.update { it.copy(selectedTab = action.tab) }
            }

            is ReportConfigAction.ToggleAccount -> {
                config.update { state ->
                    val ids = state.selectedAccountIds.toMutableSet()
                    if (action.accountId in ids) {
                        ids.remove(action.accountId)
                    } else {
                        ids.add(action.accountId)
                    }
                    state.copy(selectedAccountIds = ids)
                }
            }

            is ReportConfigAction.SelectCreditCard -> {
                config.update { it.copy(selectedCreditCardId = action.creditCardId) }
            }

            is ReportConfigAction.ToggleInvoice -> {
                config.update { state ->
                    val ids = state.selectedInvoiceIds.toMutableSet()
                    if (action.invoiceId in ids) ids.remove(action.invoiceId) else ids.add(action.invoiceId)
                    state.copy(selectedInvoiceIds = ids)
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

    // TODO: improve this
    fun buildViewerRoute(state: ReportConfigUiState): ReportRoute.Viewer? {
        if (!state.isValid) return null
        return when (state.selectedTab) {
            PerspectiveTab.ACCOUNT -> ReportRoute.Viewer(
                perspectiveType = PerspectiveTab.ACCOUNT,
                accountIds = state.selectedAccountIds.toList(),
                startDate = state.startDate.toString(),
                endDate = state.endDate.toString(),
                includeSpendingByCategory = state.includeSpendingByCategory,
                includeIncomeByCategory = state.includeIncomeByCategory,
                includeTransactionList = state.includeTransactionList,
            )

            PerspectiveTab.CREDIT_CARD -> {
                val selected = state.invoices.filter { it.id in state.selectedInvoiceIds }
                if (selected.isEmpty()) return null
                ReportRoute.Viewer(
                    perspectiveType = PerspectiveTab.CREDIT_CARD,
                    creditCardId = state.selectedCreditCardId,
                    invoiceIds = selected.map { it.id },
                    startDate = selected.minOf { it.openingDate }.toString(),
                    endDate = selected.maxOf { it.closingDate }.toString(),
                    includeSpendingByCategory = state.includeSpendingByCategory,
                    includeIncomeByCategory = state.includeIncomeByCategory,
                    includeTransactionList = state.includeTransactionList,
                )
            }
        }
    }
}
