package com.neoutils.finance.ui.modal.openInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class OpenInvoiceViewModel(
    private val creditCardId: Long,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun openInvoice(openingMonth: YearMonth) = viewModelScope.launch {
        openInvoiceUseCase(
            creditCardId,
            openingMonth
        ).onSuccess {
            modalManager.dismiss()
        }
    }
}
