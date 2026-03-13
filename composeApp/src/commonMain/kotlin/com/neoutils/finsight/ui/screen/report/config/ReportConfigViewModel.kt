package com.neoutils.finsight.ui.screen.report.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.ui.screen.home.AppRoute
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReportConfigViewModel(
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) : ViewModel() {

    private val initialConfig = ReportConfigUiState.initial()
    private val config = MutableStateFlow(initialConfig)

    init {
        selectDefaultAccount()
    }


    val uiState = combine(
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
        config,
    ) { accounts, creditCards, config ->
        val selectedTab = if (creditCards.isEmpty()) PerspectiveTab.ACCOUNT else config.selectedTab
        config.copy(
            accounts = accounts,
            creditCards = creditCards,
            selectedTab = selectedTab,
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

    private fun selectDefaultAccount() = viewModelScope.launch {
        accountRepository.getDefaultAccount()?.let { account ->
            config.update {
                it.copy(selectedAccountIds = setOf(account.id))
            }
        }
    }

    fun buildViewerRoute(state: ReportConfigUiState): AppRoute.ReportViewer? {
        if (!state.isValid) return null
        return when (state.selectedTab) {
            PerspectiveTab.ACCOUNT -> AppRoute.ReportViewer(
                perspectiveType = PerspectiveTab.ACCOUNT,
                accountIds = state.selectedAccountIds.toList(),
                startDate = state.startDate.toString(),
                endDate = state.endDate.toString(),
                includeSpendingByCategory = state.includeSpendingByCategory,
                includeTransactionList = state.includeTransactionList,
            )

            PerspectiveTab.CREDIT_CARD -> AppRoute.ReportViewer(
                perspectiveType = PerspectiveTab.CREDIT_CARD,
                creditCardId = state.selectedCreditCardId,
                startDate = state.startDate.toString(),
                endDate = state.endDate.toString(),
                includeSpendingByCategory = state.includeSpendingByCategory,
                includeTransactionList = state.includeTransactionList,
            )
        }
    }
}
