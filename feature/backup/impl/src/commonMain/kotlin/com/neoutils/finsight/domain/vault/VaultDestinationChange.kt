package com.neoutils.finsight.domain.vault

import arrow.core.Either
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moving where the copies are kept, and what is owed to the copies left behind.
 *
 * The two halves are one act and have to stay one: [VaultFolder] moves the rung, and
 * [VaultMigration] answers what could be carried across from the rung being left. What binds
 * them is the reading taken *before* the move — the destination the copies were going to —
 * and that reading is the whole reason this exists rather than living in a screen. Two
 * screens offering the change would be two places holding that "before", and one of them
 * would eventually read it after the move and offer to carry a folder into itself.
 *
 * **The change is offered from wherever the copies are shown, and answered in one place**:
 * the kept-copies screen, where the destination is chosen, and the backup screen's status
 * card, where a folder that stopped answering is announced with its two ways out (design
 * D12). Both call this.
 *
 * **Carrying is offered and never performed.** What comes back from a move is a
 * [CarryOffer] or nothing at all, and nothing is copied until [carry] is called with it —
 * because moving somebody's backups without asking is the answer design D12 rules out, and
 * copying them into a place they did not ask for them to be is the same act (design D13).
 * Nothing anywhere is removed by any of it.
 *
 * **Nothing to offer is the ordinary answer, not an edge.** The most common reason for
 * pointing at a different folder is that the last one stopped being reachable, so a source
 * that holds nothing, one that cannot be listed, and a destination that did not actually
 * move all arrive as no offer — and none of the three is worth a question somebody has to
 * dismiss.
 *
 * **A folder pointed at may already be somebody's whole history**, and never only this
 * move's own doing — a reinstall pointing at the folder it used to write to is the case
 * design D4 exists for. What that folder already holds is read once, here, before a carry
 * could add anything of its own, and it defers the sweep behind the next capture that
 * lands so that copies nobody here wrote are not the first thing retention decides about
 * (see [VaultMigration.deferSweepIfAlreadyHolding]).
 *
 * **That reading, and the offer built from it, run only when the folder actually changed —
 * by [VaultLocation], not by [VaultDestination].** Reconnecting a folder the app already
 * manages is [VaultFolder.pointAt] called again over the one it was already pointed at, and
 * it must cost nothing: arming the deferral over five copies this install wrote itself would
 * spare them from a sweep they were never in danger from, and an offer to carry a folder's
 * copies into itself is not a question anybody can sensibly answer.
 *
 * **A folder change carries too, and by the same [VaultLocation] (task 11.10).** Trading
 * folder A for folder B is no different from trading the app's own storage for a folder —
 * copies wait in A until somebody says to carry them into B, and A is never touched. What
 * makes it possible at all is that [VaultFolder.pointAt] keeps A's token reachable for one
 * folder change after it stops being the one in force
 * ([com.neoutils.finsight.domain.vault.service.BackupFolder.forgetPrevious]), and
 * [pointAtFolder] reads the folder before and after the move as locations rather than as the
 * destination enum, precisely so [VaultDestinations.rungFor] can tell A and B apart even
 * though both answer [VaultDestination.USER_FOLDER].
 *
 * **The offer is answered exactly once, and the previous token is dropped either way.** A
 * yes carries and then forgets A ([carry]); a no forgets A without carrying anything
 * ([declineCarry], reached through [com.neoutils.finsight.ui.modal.carryCopies.CarryCopiesModal]
 * being dismissed rather than answered). Both leave the lifecycle symmetric: since nothing
 * was ever removed from A, pointing back at it later is still exactly the case this class
 * already handles, and it earns a fresh offer of its own — built from wherever the person is
 * standing when they do, never from what this offer once was.
 */
class VaultDestinationChange(
    private val folder: VaultFolder,
    private val migration: VaultMigration,
) {

    /**
     * Puts the folder picker up and, if a folder was chosen, moves the vault onto it.
     *
     * Answers the offer the move earned, or null — for a picker somebody closed, which is
     * not a failure and changes nothing, as much as for a move with nothing behind it.
     *
     * It is the same call whether nothing has ever been pointed at, the link has fallen, or
     * the person wants a different folder: one machine, three moments (design D4).
     */
    suspend fun pointAtFolder(context: PlatformContext): Either<BackupError, CarryOffer?> {
        val before = folder.location

        return folder.pointAt(context).map { chosen ->
            val after = folder.location

            if (chosen && after != before) {
                // Read before the carry is ever offered, let alone answered: what the
                // folder already holds at this instant is never this call's own doing,
                // and it is what a fresh install adopting somebody's old folder looks
                // like from here (see VaultMigration.deferSweepIfAlreadyHolding).
                //
                // Gated on the folder having actually changed, by identity and not only
                // by rung — a reconnect of the one already in force answers `chosen` too,
                // and would otherwise re-arm this over copies this install wrote itself.
                migration.deferSweepIfAlreadyHolding(after)
                offerFrom(before)
            } else {
                null
            }
        }
    }

    /**
     * Moves the vault back to the app's own storage.
     *
     * It removes nothing and forgets nothing: the copies in the folder stay in it, and the
     * folder stays remembered so that choosing it again leads back to them (design D4). It
     * is also one of the two answers to a folder that has gone (design D12), and there the
     * offer finds nothing to carry — an unreadable folder is not one anything can be read
     * out of.
     */
    suspend fun keepInsideApp(): CarryOffer? {
        val before = folder.location
        folder.keepInsideApp()
        return offerFrom(before)
    }

    /**
     * Copies what [offer] counted, and says how far it got.
     *
     * The source is listed again by [VaultMigration] rather than taken from the offer: a
     * listing is a reading of the destination at the moment of the call and never a record
     * of one (design D9), so a copy that left while the question was on the screen is simply
     * not carried.
     *
     * **The folder left behind is forgotten once everything owed has landed, and not
     * before** (task 11.10). A run that stopped partway leaves the rest still only in the
     * source, so the token that still addresses it stays — a person may yet retry, and the
     * folder must still resolve when they do. Only [MigrationOutcome.Carried] means there is
     * nothing left there this app does not also have.
     */
    suspend fun carry(offer: CarryOffer): MigrationOutcome {
        val outcome =
            withContext(Dispatchers.Default) { migration.carry(from = offer.from, to = offer.to) }
        if (outcome is MigrationOutcome.Carried) folder.forgetPreviousFolder()
        return outcome
    }

    /**
     * Answers no to the offer beside a folder change (task 11.10).
     *
     * The folder left behind is forgotten here too, on purpose: the operation is symmetric,
     * so somebody who changes their mind points back at it and is offered a carry from
     * wherever they had moved on to — nothing about declining makes anything unrecoverable,
     * because nothing was ever removed from the folder being left in the first place.
     */
    fun declineCarry() = folder.forgetPreviousFolder()

    /** What could be carried out of [from] into the rung now in force, or nothing to offer. */
    private suspend fun offerFrom(from: VaultLocation): CarryOffer? {
        val to = folder.location
        val copies = withContext(Dispatchers.Default) { migration.carriable(from, to) }

        return if (copies.isEmpty()) null else CarryOffer(from = from, to = to, copies = copies.size)
    }
}

/**
 * The copies left behind by a move, as the question about them is put.
 *
 * It carries both locations and not just the count, because the answer arrives later — a
 * sheet is on the screen meanwhile — and by then "where the copies were" is no longer
 * readable anywhere: the move has already happened, and only a [VaultLocation] can still
 * tell that folder apart from whichever one is in force by the time the answer comes back
 * (task 11.10).
 */
data class CarryOffer(
    val from: VaultLocation,
    val to: VaultLocation,
    /** How many the listing that raised the question counted. */
    val copies: Int,
)
