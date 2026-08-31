package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
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
 * **Only what the destination's retention holds** — the newest ones, in the order a listing
 * already answers in. Carrying forty across so the next sweep can remove twenty of them is
 * traffic thrown away (design D13), and the limit is read from the one place that owns it
 * ([copiesKept]) rather than restated.
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
     * the two rungs are the same one, the source holds nothing, or the source could not be
     * read at all. None of the three is worth putting a question to somebody about, and the
     * last one is the ordinary state of a folder somebody is leaving *because* it went away.
     */
    suspend fun carriable(
        from: VaultDestination,
        to: VaultDestination,
    ): List<StoredBackup> {
        if (from == to) return emptyList()
        return withinRetention(destinations.rungFor(from).list().getOrNull().orEmpty())
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
        from: VaultDestination,
        to: VaultDestination,
    ): MigrationOutcome {
        if (from == to) return MigrationOutcome.NothingToCarry

        val source = destinations.rungFor(from)
        val target = destinations.rungFor(to)

        val listed = source.list().getOrNull() ?: return MigrationOutcome.Interrupted(
            copies = 0,
            error = BackupError.EXPORT_FAILED,
        )
        val copies = withinRetention(listed)
        if (copies.isEmpty()) return MigrationOutcome.NothingToCarry

        var carried = 0
        for (copy in copies) {
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
     * The newest copies the destination's retention holds, or all of them where the person
     * has asked that nothing be removed.
     *
     * A listing already answers newest first ([com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST]),
     * so the limit is a `take` and never a second ordering — two ways of deciding which copy
     * is the newest is two ways of disagreeing about which one is dropped.
     */
    private fun withinRetention(copies: List<StoredBackup>): List<StoredBackup> =
        state.observe().value.copiesKept()?.let(copies::take) ?: copies
}

/**
 * What came of carrying the history across.
 *
 * Nothing here says anything about the source, and that is because there is nothing to say:
 * every outcome leaves it exactly as it was found (design D13).
 */
sealed interface MigrationOutcome {

    /** There was nothing to carry — the same rung, an empty source, or one that would not be read. */
    data object NothingToCarry : MigrationOutcome

    /** Every copy the retention holds is now in both places. */
    data class Carried(val copies: Int) : MigrationOutcome

    /**
     * The run stopped partway. [copies] are in both places, the rest are still only in the
     * source, and nothing anywhere was removed.
     */
    data class Interrupted(val copies: Int, val error: BackupError) : MigrationOutcome
}
