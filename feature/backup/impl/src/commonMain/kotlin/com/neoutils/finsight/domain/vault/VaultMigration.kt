package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Carrying the copies already kept to the destination the person has just moved to.
 *
 * **It copies, and it never removes** (design D13). The source keeps everything it had,
 * whether the run finished, failed halfway or was walked away from — so the worst this can
 * do is leave a set of duplicates, and the thing it can never do is lose the archive.
 * Removing the source after copying would turn any failure into a way of losing the history,
 * which is not a trade worth the disk it saves.
 *
 * **It is offered and never taken.** Nothing calls this because a preference moved; a person
 * is asked, with the number of copies in front of them, and this runs on their yes. Moving
 * somebody's backups without asking is the one answer design D12 rules out, and the same
 * reasoning holds for copying them into a place they did not ask for them to be.
 *
 * **The source may be gone, and that is the case it is built for.** The long-run reason for
 * pointing at a different folder is that the last one stopped being reachable, so a source
 * that cannot be listed is an ordinary answer here: nothing is carried, nothing is said to
 * have been, and the destination the person chose stands (spec: *o app MUST NOT impedir a
 * escolha de um destino novo por isso*).
 *
 * **Only what the destination's retention holds** — the newest ones a listing answers with.
 * Carrying forty across so the next sweep can remove twenty of them is traffic thrown away
 * (design D13), and the limit is read from the one place that owns it ([copiesKept]) rather
 * than restated.
 *
 * **They are handed over oldest first, and that order is what keeps the history intact.** A
 * destination stamps what it writes with the moment it wrote it on two of the three
 * platforms, so the order the copies go in is the order the destination will read them back
 * out in — and retention counts in that order. Replaying the history newest first would make
 * the newest copy the oldest thing in the new destination, and the first sweep after the move
 * would take it.
 *
 * **Nothing is swept at the far end.** Retention runs behind a capture that landed and never
 * on its own (design D10), so a destination that ends up holding more than the limit is left
 * holding it until the next copy is taken. That direction is the safe one: this call is
 * incapable of removing a file anywhere.
 *
 * **Every copy travels through a file of this app's own.** A destination is given a path and
 * never answers with one (design D2) — on iOS a folder's permission dies on the way through
 * a string — so a copy is written out into the app's own temporary area and handed in from
 * there, one at a time. One temporary at a time is also what keeps the disk cost of carrying
 * twenty copies to the size of one, and each is removed under [NonCancellable], because a
 * suspending call in a `finally` does not run once its coroutine is cancelled.
 */
class VaultMigration(
    private val state: BackupVaultRepository,
    private val destinations: VaultDestinations,
    private val files: BackupFileService,
) {

    /**
     * The copies that would be carried from [from] to [to], newest first — what the offer
     * puts a number on.
     *
     * Empty is every reason there is nothing to offer, and they are one answer on purpose:
     * the two locations are the same one, the source holds nothing, the source could not be
     * read at all, or [from] names a folder this app no longer has a token for
     * ([VaultDestinations.rungFor]'s *unreachable*). None of these is worth putting a
     * question to somebody about, and a source that cannot be read is the ordinary state of
     * a folder somebody is leaving *because* it went away.
     */
    suspend fun carriable(
        from: VaultLocation,
        to: VaultLocation,
    ): List<StoredBackup> {
        if (from == to) return emptyList()
        return toCarry(destinations.rungFor(from).list().getOrNull().orEmpty())
    }

    /**
     * Copies what [carriable] describes into [to], and says how far it got.
     *
     * The source is listed again rather than handed in from the offer, because a listing is
     * a reading of the destination at the moment of the call and never a record of one
     * (design D9): a copy removed with a file manager while the question was on the screen is
     * simply not carried, and no error is made of it.
     */
    suspend fun carry(
        from: VaultLocation,
        to: VaultLocation,
    ): MigrationOutcome {
        if (from == to) return MigrationOutcome.NothingToCarry

        val source = destinations.rungFor(from)
        val target = destinations.rungFor(to)

        // A source that cannot even be listed answers Interrupted(0, ...) here, and not
        // NothingToCarry — unlike carriable, which reads the same failure as nothing to
        // offer (see its own doc). The two disagree on purpose: carriable is asked before
        // anyone has said yes, so silence is the honest answer to a question nobody may ever
        // repeat; this runs on that yes, so a listing that failed is a carry that failed,
        // even though it failed before touching a single copy. This predates task 11.10 and
        // is not a defect it introduced — an app-storage source that could not be listed
        // read this way before a folder even existed to be unreadable, previous or current.
        val listed = source.list().getOrNull() ?: return MigrationOutcome.Interrupted(
            copies = 0,
            error = BackupError.EXPORT_FAILED,
        )
        val copies = toCarry(listed)
        if (copies.isEmpty()) return MigrationOutcome.NothingToCarry

        var carried = 0
        for (copy in copies.asReversed()) {
            when (val outcome = carryOne(copy, source, target)) {
                null -> carried++
                else -> return MigrationOutcome.Interrupted(copies = carried, error = outcome)
            }
        }

        return MigrationOutcome.Carried(carried)
    }

    /**
     * One copy, out of the source into a file of this app's own and in from there — or the
     * failure that stopped it.
     *
     * A copy the source no longer holds is not a failure and not a carry: it left between
     * the listing and this call, which is exactly what a listing being a reading means
     * (design D9), and the run goes on to the next one.
     */
    private suspend fun carryOne(
        copy: StoredBackup,
        source: BackupDestination,
        target: BackupDestination,
    ): BackupError? {
        val scratch = files.newCapturePath().getOrNull() ?: return BackupError.EXPORT_FAILED

        return try {
            source.copyOut(copy, scratch).fold(
                ifLeft = { it },
                ifRight = { readOut ->
                    if (!readOut) {
                        null
                    } else {
                        target.put(scratch, copy.name).fold(ifLeft = { it }, ifRight = { null })
                    }
                },
            )
        } finally {
            withContext(NonCancellable) { files.discard(scratch) }
        }
    }

    /**
     * Notices that [to] already held copies before anything of this move had a chance to
     * put one there, and defers the sweep behind the next capture that lands in it.
     *
     * **It is asked once, from [VaultDestinationChange.pointAtFolder], at the one instant
     * this is knowable:** right after the rung has moved and strictly before [carry] —
     * offered separately, and only ever run on somebody's yes — could have added anything
     * of its own. What it finds already there is therefore never this move's own doing.
     * On the folder rung it is, most often, a previous install's whole history, found
     * again by pointing at the same folder (design D4) — reached by somebody who has just
     * lost everything, and exactly who a sweep must not surprise on the strength of a
     * limit they have not even seen yet.
     *
     * A folder that answers empty, or one that cannot be listed at all, arms nothing:
     * there is nothing on it yet that a sweep landing next could take by surprise, and the
     * ordinary first capture into a freshly chosen folder goes on sweeping exactly as
     * before.
     *
     * It is [to]'s [VaultLocation.destination] the deferral is armed for
     * ([BackupVaultRepository.deferNextSweep]), not "the next sweep, whichever destination it
     * lands in" — the flag this used to be armed and spent no destination at all, which is
     * what let it protect a folder nobody had just adopted, or fail to protect the one that
     * was owed the wait (see [BackupVaultRepository.consumeSweepDeferral]). The deferral
     * itself still knows only [VaultDestination] and not which folder: two folder changes
     * inside the window before the next capture lands is not a case task 11.10 has to make
     * safe, and the cost of it staying coarse is at most one copy kept a little longer
     * (design D10's own safe direction).
     */
    suspend fun deferSweepIfAlreadyHolding(to: VaultLocation) {
        val already = destinations.rungFor(to).list().getOrNull()
        if (!already.isNullOrEmpty()) state.deferNextSweep(to.destination)
    }

    /**
     * What a listing of the source offers a carry: the newest copies the destination's
     * retention holds, or all of them where the person has asked that nothing be removed —
     * newest first, as a listing answers — and never the copy taken before a migration.
     *
     * A listing already answers newest first ([com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST]),
     * so the limit is a `take` and never a second ordering — two ways of deciding which copy
     * is the newest is two ways of disagreeing about which one is dropped. Which end the
     * copying starts at is [carry]'s, and it is the other one.
     *
     * The copy taken before a migration stays where it is because that is where it belongs:
     * it goes into the app's own storage whatever destination is chosen, since on the way up
     * a folder may not be reachable. Carried into a folder it would be a second file under
     * the one name reserved for it — a name retention refuses to sweep, so nothing would
     * ever remove it — and it is not lost by staying: a destination that is not the app's
     * own storage lists it from there ([VaultDestinations.list]).
     */
    private fun toCarry(copies: List<StoredBackup>): List<StoredBackup> {
        val dated = copies.filterNot { it.name == PRE_MIGRATION_BACKUP_NAME }
        return state.observe().value.copiesKept()?.let(dated::take) ?: dated
    }
}

/**
 * What came of carrying the history across.
 *
 * Nothing here says anything about the source, and that is because there is nothing to say:
 * every outcome leaves it exactly as it was found (design D13).
 */
sealed interface MigrationOutcome {

    /**
     * There was nothing to carry from [VaultMigration.carry]'s own point of view — the same
     * location twice, or a source that was read and answered empty, or one retention leaves
     * nothing of once it has. **Not** what an unreadable source answers here — see
     * [Interrupted].
     */
    data object NothingToCarry : MigrationOutcome

    /** Every copy the retention holds is now in both places. */
    data class Carried(val copies: Int) : MigrationOutcome

    /**
     * The run stopped partway. [copies] are in both places, the rest are still only in the
     * source, and nothing anywhere was removed.
     *
     * [copies] of zero covers two different moments that read alike from here: a source that
     * refused the very first listing, and one that listed fine but failed on its first copy.
     * Neither is [NothingToCarry] — that answer is reserved for a source read successfully
     * and found to have nothing worth carrying, which an unreadable source is not proof of
     * either way (design D9's own rule, carried into this call). This is not something task
     * 11.10 introduced: an app-storage source that could not be listed answered this way
     * before a second, folder-shaped source ever existed to be unreadable too.
     */
    data class Interrupted(val copies: Int, val error: BackupError) : MigrationOutcome
}
