package com.neoutils.finsight.ui.modal.reopenInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ReopenInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ReopenInvoiceViewModel(
    private val invoiceId: Long,
    private val reopenInvoiceUseCase: ReopenInvoiceUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun reopenInvoice() = viewModelScope.launch {
        reopenInvoiceUseCase(
            invoiceId = invoiceId,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(ReopenInvoice)
            modalManager.dismissAll()
        }
    }
}
