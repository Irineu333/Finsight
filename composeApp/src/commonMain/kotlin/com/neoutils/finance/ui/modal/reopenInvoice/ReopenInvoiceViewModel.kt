package com.neoutils.finance.ui.modal.reopenInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class ReopenInvoiceViewModel(
    private val invoiceId: Long,
    private val reopenInvoiceUseCase: ReopenInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun reopenInvoice() = viewModelScope.launch {
        reopenInvoiceUseCase(
            invoiceId = invoiceId,
        ).onRight {
            modalManager.dismissAll()
        }
    }
}
