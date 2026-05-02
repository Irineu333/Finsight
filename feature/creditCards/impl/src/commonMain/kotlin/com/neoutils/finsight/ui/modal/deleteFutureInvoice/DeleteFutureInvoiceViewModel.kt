package com.neoutils.finsight.ui.modal.deleteFutureInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteFutureInvoice
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteFutureInvoiceViewModel(
    private val invoice: Invoice,
    private val deleteFutureInvoiceUseCase: DeleteFutureInvoiceUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteInvoice() = viewModelScope.launch {
        deleteFutureInvoiceUseCase(
            invoiceId = invoice.id,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(DeleteFutureInvoice)
            modalManager.dismissAll()
        }
    }
}
