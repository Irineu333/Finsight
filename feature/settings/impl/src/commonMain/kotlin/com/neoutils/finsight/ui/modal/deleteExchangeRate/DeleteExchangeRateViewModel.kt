package com.neoutils.finsight.ui.modal.deleteExchangeRate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.database.repository.RateArchive
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteExchangeRate
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.feature.backup.api.CaptureRefusal
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.api.VaultOfferState
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Removing one rate observation — and, when the copy owed before it could not be taken,
 * asking rather than either crashing or removing regardless.
 *
 * **The removal is confirmed rather than performed on the press.** It was the one deletion
 * of the user's own data this app performed straight from a button, and there is nothing
 * about a rate that makes it the exception: a rate observed by mistake from an operation
 * since deleted has no other path that reaches it, so a mistaken tap costs typed work
 * exactly as every other deletion does.
 *
 * **The vault is offered here too**, when it has never been offered anywhere before. The
 * offer rides on whichever destructive confirmation the person reaches first, and it
 * belongs beside a risk — which is here, and no longer on a form that may only be
 * registering a rate.
 */
class DeleteExchangeRateViewModel(
    private val rate: ExchangeRate,
    private val exchangeRateRepository: RateArchive,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    vaultOffer: VaultOffer,
    coverage: PreventiveCoverage,
) : ViewModel() {

    private val refusal = CaptureRefusal()

    /** Why no copy could be taken, while the question about removing anyway is up. */
    val captureRefusal: StateFlow<UiText?> = refusal.reason

    /**
     * The vault offered beside this removal, and the box beside the offer.
     *
     * Asked for as the sheet is built, and answered as the removal starts: whether the
     * offer stands at all, and whether it arrives ticked, are the vault's own.
     */
    val offer = VaultOfferState(
        offer = vaultOffer,
        coverage = coverage,
        action = DestructiveAction.REMOVE_EXCHANGE_RATE,
    )

    /**
     * Whether a copy is genuinely kept before the observation goes, which is what the sheet
     * says instead of calling the loss permanent.
     *
     * Asked about *this* action and answered in the domain: a screen carrying its own idea
     * of which removals are worth a copy would be a second owner of that rule (design D7).
     */
    val keepsCopy: StateFlow<Boolean> get() = offer.keepsCopy

    /**
     * The dismissal belongs **inside** the write, as it does in every other sheet of this
     * app: dismissing a `ModalBottomSheet` clears its `ViewModelStore`, which cancels this
     * very scope — so a button that both removes and dismisses cancels its own write at the
     * first suspension point.
     */
    fun remove() = viewModelScope.launch {
        // The offer is answered before the removal and never after: a box left ticked has
        // to have turned the vault on by the time the removal asks it for the copy, which
        // is the next thing that happens, and a box left unticked is a refusal only once
        // the removal actually goes ahead.
        offer.settle()

        refusal.attempt { withoutCopy ->
            exchangeRateRepository.remove(rate, withoutCopy)
            analytics.logEvent(DeleteExchangeRate(rate.currency, rate.counterCurrency))
            // The form underneath goes with it: what it was open on no longer exists.
            modalManager.dismissAll()
        }
    }

    /** The person said to remove with nothing kept back; the removal goes on from where it waits. */
    fun removeWithoutCopy() = refusal.answer(proceed = true)

    /** Answered no, or walked away from the question — which is the same answer. */
    fun abandonRemoval() = refusal.answer(proceed = false)
}
