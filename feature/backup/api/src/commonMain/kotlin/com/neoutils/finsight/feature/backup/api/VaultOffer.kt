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
 * **The offer stands while the vault is off**, on every destructive confirmation and not
 * only the first: an offer that vanished after one no would take away the only place the
 * vault can be turned on from where it matters. What changes on the ones after the first is
 * the tone — the box comes unticked and says so — so the offer stays present without
 * turning into insistence.
 *
 * **A confirmation asks for the offer; it never decides there is one.** Whether the vault
 * is already on, and whether it has been declined before, are the vault's own — a screen
 * that read either would be a second place the rule lives.
 */
fun interface VaultOffer {

    /**
     * The offer this confirmation carries, or null when there is nothing to offer because
     * the vault is already on.
     */
    fun offer(): VaultOfferTerms?

    companion object {

        /**
         * Offers nothing, ever. Not a default — the app binds the vault — but what a test
         * whose subject is the deletion rather than the offer hands to it.
         */
        val None = VaultOffer { null }
    }
}

/**
 * What accepting costs, what was answered last time, and the two answers themselves.
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

    /**
     * Whether this offer has been left unticked and gone past before.
     *
     * It is the whole of what separates the two shapes the offer takes: the box starts
     * ticked where this is false and unticked where it is true, and the sentence beside it
     * changes with it. One fact, read here and nowhere else.
     */
    val wasDeclined: Boolean,

    private val decline: () -> Unit,
    private val turnOn: suspend () -> Unit,
) {

    /**
     * Turns the whole vault on, and takes the copy that turning it on means.
     *
     * It suspends until that copy is either in the destination or has failed, and that is
     * what keeps a deletion to one file: the action's own trigger asks for the same copy a
     * moment later and finds the archive already covered (design D8). Accepting and then
     * deleting is one occasion, however many triggers see it.
     */
    suspend fun accept() = turnOn()

    /**
     * Records that the offer was left unticked and the action went ahead anyway — which is
     * what the next confirmation reads to arrive unticked and worded as a reminder.
     *
     * It is the going ahead that is recorded, never the showing: somebody who reads the
     * offer and cancels the deletion has answered nothing.
     */
    fun declineOnce() = decline()
}

/**
 * The offer a confirmation carries and the box beside it, from the moment the sheet is
 * built to the moment the action starts.
 *
 * It exists because five confirmations across three features carry the same offer, and the
 * two things easy to get wrong about it are not the rendering. The box's starting state is
 * the previous answer, because the offer is made rather than merely displayed — and a box
 * left ticked turns the vault on **before** the action runs, because a vault turned on
 * afterwards has nothing left to copy. Five view models deciding either would be five
 * chances for one to decide it differently.
 */
class VaultOfferState(offer: VaultOffer) {

    /** What this confirmation has to offer, or null on a vault that is already on. */
    val terms: VaultOfferTerms? = offer.offer()

    private val _isAccepted = MutableStateFlow(terms != null && !terms.wasDeclined)

    /**
     * Whether the box is ticked. It starts ticked on an offer nobody has turned down, and
     * unticked on one somebody already has — the answer given last time, offered again.
     */
    val isAccepted: StateFlow<Boolean> = _isAccepted.asStateFlow()

    fun setAccepted(accepted: Boolean) {
        _isAccepted.value = accepted
    }

    /**
     * Answers the offer: turns the whole vault on when the box was left ticked, and records
     * the refusal when it was not. It does nothing at all where there was never an offer.
     *
     * **Called before the destructive action, never after.** The vault has to be on by the
     * time the action asks it for the copy, which is the next thing that happens — and the
     * copy taken here is that copy, which is why one deletion accepted from the offer still
     * produces one file. It is also the only moment an answer exists: a sheet that goes up
     * and is dismissed has been read, not answered.
     */
    suspend fun settle() {
        val terms = terms ?: return

        if (_isAccepted.value) terms.accept() else terms.declineOnce()
    }
}
