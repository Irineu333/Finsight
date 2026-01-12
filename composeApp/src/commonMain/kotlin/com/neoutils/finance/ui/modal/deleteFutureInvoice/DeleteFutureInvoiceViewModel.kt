package com.neoutils.finance.ui.modal.deleteFutureInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteFutureInvoiceViewModel(
    private val invoice: Invoice,
    private val deleteFutureInvoiceUseCase: DeleteFutureInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteInvoice() = viewModelScope.launch {
        deleteFutureInvoiceUseCase(invoice.id).onSuccess {
            modalManager.dismissAll()
        }
    }
}
