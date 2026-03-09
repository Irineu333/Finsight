package com.neoutils.finsight.ui.screen.report.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.ui.screen.home.AppRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ReportConfigViewModel(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) : ViewModel() {

    private val config = MutableStateFlow(ReportConfigUiState())

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        config,
    ) { accounts, creditCards, config ->
        config.copy(
            accounts = accounts,
            creditCards = creditCards,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportConfigUiState(),
    )

    fun onAction(action: ReportConfigAction) = viewModelScope.launch {
        when (action) {
            is ReportConfigAction.SelectPerspective -> {
                config.update { it.copy(selectedTab = action.tab) }
            }
            is ReportConfigAction.ToggleAccount -> {
                config.update { state ->
                    val ids = state.selectedAccountIds.toMutableSet()
                    if (action.accountId in ids && ids.size > 1) {
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
            is ReportConfigAction.SelectStartDate -> {
                config.update { it.copy(startDate = action.date) }
            }
            is ReportConfigAction.SelectEndDate -> {
                config.update { it.copy(endDate = action.date) }
            }
            is ReportConfigAction.ToggleSpendingByCategory -> {
                config.update { it.copy(includeSpendingByCategory = action.enabled) }
            }
            is ReportConfigAction.ToggleTransactionList -> {
                config.update { it.copy(includeTransactionList = action.enabled) }
            }
        }
    }

    fun buildViewerRoute(state: ReportConfigUiState): AppRoute.ReportViewer? {
        if (!state.isValid) return null
        return when (state.selectedTab) {
            PerspectiveTab.ACCOUNT -> AppRoute.ReportViewer(
                perspectiveType = "ACCOUNT",
                accountIds = state.selectedAccountIds.toList(),
                startDate = state.startDate!!.toString(),
                endDate = state.endDate!!.toString(),
                includeSpendingByCategory = state.includeSpendingByCategory,
                includeTransactionList = state.includeTransactionList,
            )
            PerspectiveTab.CREDIT_CARD -> AppRoute.ReportViewer(
                perspectiveType = "CREDIT_CARD",
                creditCardId = state.selectedCreditCardId,
                startDate = state.startDate!!.toString(),
                endDate = state.endDate!!.toString(),
                includeSpendingByCategory = state.includeSpendingByCategory,
                includeTransactionList = state.includeTransactionList,
            )
        }
    }
}
