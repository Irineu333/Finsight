package com.neoutils.finsight.feature.backup.api

import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The vault, offered where the risk it covers appears.
 *
 * A vault that is born off and lives behind a settings screen has the problem the manual
 * backup already has: it must be remembered. So the offer arrives beside a destructive
 * confirmation instead — the moment somebody is about to lose something is the moment the
 * protection means anything (spec: *a primeira ação destrutiva do usuário SHALL trazer a
 * oferta junto da confirmação*).
 *
 * **A confirmation asks for the offer; it never decides there is one.** Whether the vault
 * is already on, and whether the question has been put once already, are the vault's own —
 * a screen that read either would be a second place the rule lives.
 */
fun interface VaultOffer {

    /**
     * The offer this confirmation carries, or null when there is nothing to offer — the
     * vault is on, or it has already been offered once.
     *
     * **Asking is what records that the offer was made**, whatever the answer turns out to
     * be: what must not happen twice is the asking, and somebody who said no is not asked
     * again every time they delete something.
     */
    fun offerOnce(): VaultOfferTerms?

    companion object {

        /**
         * Offers nothing, ever. Not a default — the app binds the vault — but what a test
         * whose subject is the deletion rather than the offer hands to it.
         */
        val None = VaultOffer { null }
    }
}

/**
 * What accepting costs, and the accepting itself.
 *
 * [intervalLabel] is here because the sentence beside the box has to state the whole price:
 * accepting turns the vault on — every trigger it has, from now on, at that interval
 * (design D1) — and not this one copy. Saying so is what separates an offer from a trick.
 *
 * [accept] is deliberately not "remember this answer for later": the vault has to be on by
 * the time the action asks it for the copy, which is the next thing that happens.
 */
class VaultOfferTerms(
    val intervalLabel: UiText,
    private val turnOn: () -> Unit,
) {

    /** Turns the whole vault on. */
    fun accept() = turnOn()
}

/**
 * The offer a confirmation carries and the box beside it, from the moment the sheet is
 * built to the moment the action starts.
 *
 * It exists because five confirmations across three features carry the same offer, and the
 * two things easy to get wrong about it are not the rendering. The box **starts ticked**,
 * because the offer is made rather than merely displayed. And a box left ticked turns the
 * vault on **before** the action runs, because a vault turned on afterwards has nothing
 * left to copy. Five view models deciding either would be five chances for one to decide it
 * differently.
 *
 * Asking is done here, once, as the sheet is built — which is what records that the offer
 * was made, whatever the answer turns out to be.
 */
class VaultOfferState(offer: VaultOffer) {

    /** What this confirmation has to offer, or null when there is nothing left to offer. */
    val terms: VaultOfferTerms? = offer.offerOnce()

    private val _isAccepted = MutableStateFlow(terms != null)

    /** Whether the box is still ticked. It starts ticked wherever there is an offer. */
    val isAccepted: StateFlow<Boolean> = _isAccepted.asStateFlow()

    fun setAccepted(accepted: Boolean) {
        _isAccepted.value = accepted
    }

    /**
     * Turns the whole vault on when the box was left ticked, and does nothing at all
     * otherwise — including where there was never an offer to tick.
     *
     * **Called before the destructive action, never after.** The vault has to be on by the
     * time the action asks it for the copy, which is the next thing that happens.
     */
    fun acceptIfTicked() {
        if (_isAccepted.value) terms?.accept()
    }
}
