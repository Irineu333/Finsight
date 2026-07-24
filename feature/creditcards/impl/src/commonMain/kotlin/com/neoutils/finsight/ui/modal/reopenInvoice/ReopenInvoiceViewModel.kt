package com.neoutils.finsight.ui.modal.reopenInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ReopenInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
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
            modalManager.showError(it.toUiMessage())
        }.onRight {
            analytics.logEvent(ReopenInvoice)
            modalManager.dismissAll()
        }
    }

    /**
     * A refused reopen has a reason the user can act on — reopening is only allowed on
     * the latest closed invoice, so a later cycle already being active blocks it. Without
     * this the sheet just did not close and said nothing.
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
