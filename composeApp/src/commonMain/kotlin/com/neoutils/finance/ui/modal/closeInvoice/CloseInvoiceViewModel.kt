package com.neoutils.finance.ui.modal.closeInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class CloseInvoiceViewModel(
    private val invoiceId: Long,
    private val closeInvoiceUseCase: CloseInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun closeInvoice(closingDate: LocalDate) = viewModelScope.launch {
        closeInvoiceUseCase(
            invoiceId,
            closingDate
        ).onSuccess {
            modalManager.dismissAll()
        }
    }
}
