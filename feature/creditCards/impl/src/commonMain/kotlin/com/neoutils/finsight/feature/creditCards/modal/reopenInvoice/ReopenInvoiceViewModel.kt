package com.neoutils.finsight.feature.creditCards.modal.reopenInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.creditCards.event.ReopenInvoice
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.creditCards.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
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
