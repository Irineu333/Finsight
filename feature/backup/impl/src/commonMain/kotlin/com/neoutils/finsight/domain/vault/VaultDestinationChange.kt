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
                migration.deferSweepIfAlreadyHolding(after.destination)
                offerFrom(before.destination)
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
        val before = folder.rung.inForce
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
     */
    suspend fun carry(offer: CarryOffer): MigrationOutcome =
        withContext(Dispatchers.Default) { migration.carry(from = offer.from, to = offer.to) }

    /** What could be carried out of [from] into the rung now in force, or nothing to offer. */
    private suspend fun offerFrom(from: VaultDestination): CarryOffer? {
        val to = folder.rung.inForce
        val copies = withContext(Dispatchers.Default) { migration.carriable(from, to) }

        return if (copies.isEmpty()) null else CarryOffer(from = from, to = to, copies = copies.size)
    }
}

/**
 * The copies left behind by a move, as the question about them is put.
 *
 * It carries both rungs and not just the count, because the answer arrives later — a sheet
 * is on the screen meanwhile — and by then "where the copies were" is no longer readable
 * anywhere: the move has already happened.
 */
data class CarryOffer(
    val from: VaultDestination,
    val to: VaultDestination,
    /** How many the listing that raised the question counted. */
    val copies: Int,
)
