package com.neoutils.finsight.ui.modal.deleteCurrency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
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
 * Deleting a currency — and, when the copy owed before it could not be taken, asking rather
 * than either crashing or deleting regardless.
 *
 * The refusal reaches this sheet because this is the screen the person is standing in front
 * of, and going on without a copy is theirs to answer. Not answering it leaves the currency
 * and every observation naming it exactly where they are.
 *
 * **The vault is offered here too**, when it has never been offered anywhere before. The
 * offer rides on whichever destructive confirmation the person reaches first, and a
 * currency taking every rate that names it is one of the five that carry it.
 */
class DeleteCurrencyViewModel(
    private val code: String,
    private val deleteCurrency: DeleteCurrencyUseCase,
    private val modalManager: ModalManager,
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
     * Whether a copy is genuinely kept before the currency goes, which is what the sheet
     * says instead of calling the loss permanent.
     *
     * Asked about *this* action and answered in the domain: a screen carrying its own idea
     * of which deletions are worth a copy would be a second owner of that rule (design D7).
     */
    val keepsCopy = coverage.keepsCopyBefore(DestructiveAction.DELETE_CURRENCY)

    fun delete() = viewModelScope.launch {
        // The offer is answered before the removal and never after: a box left ticked has
        // to have turned the vault on by the time the deletion asks it for the copy, which
        // is the next thing that happens, and a box left unticked is a refusal only once
        // the deletion actually goes ahead.
        offer.settle()

        refusal.attempt { withoutCopy ->
            deleteCurrency(code, withoutCopy)
                .onRight { modalManager.dismissAll() }
                // The refusals — an account or a budget denominates it — reach the user in
                // the one place this app states a refusal, rather than as a dead button.
                .onLeft { modalManager.showError(it.toUiText()) }
        }
    }

    /** The person said to delete with nothing kept back; the deletion goes on from where it waits. */
    fun deleteWithoutCopy() = refusal.answer(proceed = true)

    /** Answered no, or walked away from the question — which is the same answer. */
    fun abandonDeletion() = refusal.answer(proceed = false)
}
