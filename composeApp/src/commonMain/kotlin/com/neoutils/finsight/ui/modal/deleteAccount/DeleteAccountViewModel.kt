package com.neoutils.finsight.ui.modal.deleteAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteAccountViewModel(
    private val account: Account,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteAccount() = viewModelScope.launch {
        deleteAccountUseCase(account).onRight {
            modalManager.dismissAll()
        }.onLeft {
            // TODO: Show error message to user
        }
    }
}
