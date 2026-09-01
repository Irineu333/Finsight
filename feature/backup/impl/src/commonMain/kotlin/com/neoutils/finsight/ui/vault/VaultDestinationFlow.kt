package com.neoutils.finsight.ui.vault

import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.vault.CarryOffer
import com.neoutils.finsight.domain.vault.MigrationOutcome
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_carry_done
import com.neoutils.finsight.resources.backup_carry_partial
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.carryCopies.CarryCopiesModal
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Changing where the copies go, as a screen performs it: the picker, the move, the question
 * about what was left behind, and the sentence each outcome is owed.
 *
 * **Two screens offer this change and there is one of it.** The backup screen offers it
 * beside a folder that has gone (design D12), and the kept-copies screen offers it from the
 * header that names the destination (design D15) — and both used to carry their own copy of
 * these four steps, identical line for line but for the name of the reading each takes
 * afterwards. What was duplicated was not plumbing: which outcome deserves a word and which
 * word it is are decisions, and *carried* and *interrupted* being different sentences is one
 * the spec asks for by name, because somebody who said yes to carrying twenty copies has to
 * learn that some of them are still only in the old place.
 *
 * **Nothing here decides anything about the vault.** The move, the reading taken before it
 * and whether there is anything to offer carrying are all [VaultDestinationChange]'s
 * (design D13), which is what keeps the destination the copies were going to from being read
 * twice. This is the part that needs a person in front of it: a picker, a sheet, and a
 * result said out loud.
 *
 * @param refresh what the calling screen does to read its own destination again. It is the
 * one thing the two screens genuinely differ in — the card counts what is there, the list
 * *is* what is there — so it is a parameter rather than a fifth step of this.
 */
class VaultDestinationFlow(
    private val change: VaultDestinationChange,
    private val modalManager: ModalManager,
    private val scope: CoroutineScope,
    private val refresh: () -> Unit,
) {

    /**
     * Puts the folder picker up and, if a folder was chosen, moves the vault onto it.
     *
     * **Nothing is carried across on its own, and nothing is ever removed.** A picker
     * somebody closed changes nothing and says nothing; only a real failure — the folder
     * could not be prepared — reaches the person, because only that one leaves them with
     * something to do.
     */
    fun chooseFolder(context: PlatformContext) {
        scope.launch {
            change.pointAtFolder(context).fold(
                ifLeft = ::fail,
                ifRight = { offer ->
                    refresh()
                    offer?.let(::offerToCarry)
                },
            )
        }
    }

    /**
     * Moves the vault back to the app's own storage.
     *
     * It removes nothing and forgets nothing: the copies in the folder stay in it, and the
     * folder stays remembered so that choosing it again leads back to them (design D4). It
     * is also one of the two answers to a folder that has gone (design D12).
     */
    fun keepInsideApp() {
        scope.launch {
            val offer = change.keepInsideApp()
            refresh()
            offer?.let(::offerToCarry)
        }
    }

    /**
     * Asks whether the copies left behind should be written into the destination that is now
     * in force.
     *
     * The question is only put where there is something to answer, and that is decided
     * before this is reached: an offer exists only when a listing counted copies on the rung
     * being left (design D13).
     */
    private fun offerToCarry(offer: CarryOffer) {
        modalManager.show(
            CarryCopiesModal(
                copies = offer.copies,
                onCarry = { carry(offer) },
                onDeclined = change::declineCarry,
            )
        )
    }

    /**
     * Carries the copies across, and says what came of it.
     *
     * Both outcomes are worth a word and they are different words: everything arrived, or it
     * stopped partway. Neither says anything about the source, because nothing anywhere was
     * removed from it.
     *
     * The destination is read again on the way out: what the screen shows has just grown.
     */
    private fun carry(offer: CarryOffer) {
        scope.launch {
            when (change.carry(offer)) {
                is MigrationOutcome.Carried -> {
                    modalManager.showSuccess(UiText.Res(Res.string.backup_carry_done))
                }

                is MigrationOutcome.Interrupted -> {
                    modalManager.showError(UiText.Res(Res.string.backup_carry_partial))
                }

                MigrationOutcome.NothingToCarry -> Unit
            }

            refresh()
        }
    }

    private fun fail(error: BackupError) = modalManager.showError(error.toUiText())
}
