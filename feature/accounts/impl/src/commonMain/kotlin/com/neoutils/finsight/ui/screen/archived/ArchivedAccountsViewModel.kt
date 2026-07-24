package com.neoutils.finsight.ui.screen.archived

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.ui.model.toArchivedUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ArchivedAccountsViewModel(
    accountRepository: IAccountRepository,
) : ViewModel() {

    val uiState = accountRepository.observeAllAccountsIncludingClosed()
        .map { accounts -> accounts.filter(Account::isArchived) }
        .map { archived ->
            if (archived.isEmpty()) {
                ArchivedAccountsUiState.Empty
            } else {
                ArchivedAccountsUiState.Content(archived.map(Account::toArchivedUi))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArchivedAccountsUiState.Loading,
        )
}
