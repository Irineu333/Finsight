package com.neoutils.finsight.ui.modal.deleteInstallment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteInstallments
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
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
 * Deleting an installment — N transactions in one decision — and, when the copy owed
 * before it could not be taken, asking rather than reporting a generic failure.
 *
 * Nothing about the installment stops the removal here: what is missing is the file that
 * would let the person undo it, and only they may say to go on without it. Until they do,
 * all N instalments are still there.
 *
 * **The vault is offered here too**, when it has never been offered anywhere before. The
 * offer rides on whichever destructive confirmation the person reaches first, and this is
 * one of the five that carry it. What is offered, and whether there is anything left to
 * offer, are both [VaultOffer]'s: this screen shows what it is handed and decides none of
 * it.
 */
class DeleteInstallmentViewModel(
    private val installment: Installment,
    private val transactions: List<Transaction>,
    private val categoryRepository: ICategoryRepository,
    private val deleteInstallmentUseCase: DeleteInstallmentUseCase,
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
    val offer = VaultOfferState(vaultOffer)

    /**
     * Whether a copy is genuinely kept before the instalments go, which is what the sheet
     * says instead of calling the loss permanent.
     *
     * Asked about *this* action and answered in the domain: a screen carrying its own idea
     * of which deletions are worth a copy would be a second owner of that rule (design D7).
     */
    val keepsCopy = coverage.keepsCopyBefore(DestructiveAction.DELETE_INSTALLMENT)

    fun deleteInstallment() = viewModelScope.launch {
        // The event still reports the category by name; the ledger hands out only
        // the dimension its nominal leg carries (design D6).
        val categoryName = transactions.firstOrNull()?.nominalDimensionId
            ?.let { dimensionId ->
                categoryRepository.getCategoryByDimensionId(dimensionId)
            }
            ?.name

        // The offer is answered before the removal and never after: a box left ticked has
        // to have turned the vault on by the time the deletion asks it for the copy, and it
        // turns the whole vault on rather than authorising this one copy (design D1), which
        // is what the sentence beside the box says. A box left unticked is a refusal only
        // once the deletion actually goes ahead.
        offer.settle()

        refusal.attempt { withoutCopy ->
            deleteInstallmentUseCase(installment, transactions, withoutCopy).onRight {
                analytics.logEvent(DeleteInstallments(installment, categoryName))
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
     * A refused deletion has a reason the user can act on — a locked invoice, or a
     * an archived card whose balance the removal would reopen. Without this the sheet
     * just did not close and said nothing, which reads as "it worked".
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is InvoiceException -> error.toUiText()
        is ClosedAccountException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
