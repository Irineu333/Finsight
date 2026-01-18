package com.neoutils.finance.ui.modal.deleteAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.usecase.DeleteAccountUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteAccountViewModel(
    private val account: Account,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteAccount() = viewModelScope.launch {
        deleteAccountUseCase(account).onSuccess {
            modalManager.dismissAll()
        }.onFailure {
            // TODO: Show error message to user
        }
    }
}
