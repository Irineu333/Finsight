package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.transaction_error_generic
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.domain.analytics.event.DeleteTransaction
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.feature.backup.api.CaptureRefusal
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferState
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Deleting a transaction — and, when the copy owed before it could not be taken, asking
 * rather than reporting a generic failure.
 *
 * A refused capture is not the same kind of event as a locked invoice: nothing about the
 * transaction stops it, and the only thing missing is the file that would let the person
 * undo it. So it reaches them as a question with two answers, and not answering it leaves
 * the transaction exactly where it is.
 *
 * **The vault is offered here too**, when it has never been offered anywhere before. The
 * offer rides on whichever destructive confirmation the person reaches first, and deleting
 * a transaction is the one most people reach first of all. What is offered, and whether
 * there is anything left to offer, are both [VaultOffer]'s.
 */
class DeleteTransactionViewModel(
    private val transaction: Transaction,
    private val categoryRepository: ICategoryRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
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
        action = DestructiveAction.DELETE_TRANSACTION,
    )

    /**
     * Whether a copy is genuinely kept before the transaction goes, which is what the sheet
     * says instead of calling the loss permanent.
     *
     * Asked about *this* action and answered in the domain: a screen carrying its own idea
     * of which deletions are worth a copy would be a second owner of that rule (design D7).
     */
    val keepsCopy: StateFlow<Boolean> get() = offer.keepsCopy

    fun deleteTransaction() = viewModelScope.launch {
        // The analytics event still reports the category by name; the ledger only
        // hands out its dimension, so the name is resolved here (design D6).
        val categoryName = transaction.nominalDimensionId
            ?.let { dimensionId ->
                categoryRepository.getCategoryByDimensionId(dimensionId)
            }
            ?.name

        // The offer is answered before the removal and never after: a box left ticked has
        // to have turned the vault on by the time the deletion asks it for the copy, which
        // is the next thing that happens, and a box left unticked is a refusal only once
        // the deletion actually goes ahead.
        offer.settle()

        refusal.attempt { withoutCopy ->
            deleteTransactionUseCase(transaction, withoutCopy).onRight {
                analytics.logEvent(DeleteTransaction(transaction, categoryName))
                modalManager.dismissAll()
            }.onLeft {
                crashlytics.recordException(it)
                modalManager.showError(it.toUiMessage())
            }
        }
    }

    /** The person said to delete with nothing kept back; the deletion goes on from where it waits. */
    fun deleteWithoutCopy() = refusal.answer(proceed = true)

    /** Answered no, or walked away from the question — which is the same answer. */
    fun abandonDeletion() = refusal.answer(proceed = false)

    /**
     * A refused deletion has a reason the user can act on — a locked invoice, an
     * archived account whose balance the removal would reopen. Without this the
     * sheet just did not close and said nothing, which in a finance app reads as
     * "it worked".
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        is ClosedAccountException -> error.toUiText()
        else -> UiText.Res(Res.string.transaction_error_generic)
    }
}
