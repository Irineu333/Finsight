package com.neoutils.finsight.ui.modal.deleteFutureInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteFutureInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.feature.backup.api.CaptureRefusal
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferState
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Deleting an invoice — and every real transaction posted to it — and, when the copy owed
 * before it could not be taken, asking rather than crashing.
 *
 * The refusal is thrown rather than returned, because the use case's `Either` names the
 * invoice's own refusals and a missing copy is not one of them. Caught here it becomes the
 * question it is; left uncaught it took the app down.
 *
 * **The vault is offered here too**, when it has never been offered anywhere before. The
 * offer rides on whichever destructive confirmation the person reaches first, and an
 * invoice taking every transaction posted to it is one of the five that carry it.
 */
class DeleteFutureInvoiceViewModel(
    private val invoice: Invoice,
    private val deleteFutureInvoiceUseCase: DeleteFutureInvoiceUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    vaultOffer: VaultOffer,
    coverage: PreventiveCoverage,
) : ViewModel() {

    private val refusal = CaptureRefusal()

    /** Why no copy could be taken, while the question about deleting anyway is up. */
    val captureRefusal: StateFlow<UiText?> = refusal.reason

    /**
     * The vault offered beside this deletion, and the box beside the offer.
     *
     * Asked for as the sheet is built, and answered as the deletion starts: whether the
     * offer stands at all, and whether it arrives ticked, are the vault's own.
     */
    val offer = VaultOfferState(
        offer = vaultOffer,
        coverage = coverage,
        action = DestructiveAction.DELETE_INVOICE,
    )

    /**
     * Whether a copy is genuinely kept before the invoice goes, which is what the sheet
     * says instead of calling the loss permanent.
     *
     * Asked about *this* action and answered in the domain: a screen carrying its own idea
     * of which deletions are worth a copy would be a second owner of that rule (design D7).
     */
    val keepsCopy: StateFlow<Boolean> get() = offer.keepsCopy

    fun deleteInvoice() = viewModelScope.launch {
        // The offer is answered before the removal and never after: a box left ticked has
        // to have turned the vault on by the time the deletion asks it for the copy, which
        // is the next thing that happens, and a box left unticked is a refusal only once
        // the deletion actually goes ahead.
        offer.settle()

        refusal.attempt { withoutCopy ->
            deleteFutureInvoiceUseCase(
                invoiceId = invoice.id,
                withoutCopy = withoutCopy,
            ).onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(DeleteFutureInvoice)
                modalManager.dismissAll()
            }
        }
    }

    /** The person said to delete with nothing kept back; the deletion goes on from where it waits. */
    fun deleteWithoutCopy() = refusal.answer(proceed = true)

    /** Answered no, or walked away from the question — which is the same answer. */
    fun abandonDeletion() = refusal.answer(proceed = false)
}
