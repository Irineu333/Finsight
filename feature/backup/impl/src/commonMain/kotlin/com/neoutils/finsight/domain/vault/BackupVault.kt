@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.vault

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseCaptureException
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toBackupError
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.backupFileName
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The one thing in the app that puts a copy in the vault's destination, and therefore the
 * one place every rule about doing so lives.
 *
 * **Every occasion that writes a copy into the destination goes through here, and that is
 * what makes design D1 true.** The switch is read first, before a path is asked for, before
 * the archive is read and before the destination is touched — so "nothing is written while
 * the vault is off" is a property of these two functions rather than a check each caller
 * remembers to perform. A trigger added later inherits it by having nowhere else to go: the
 * manual export leaves the vault entirely and lives in
 * [com.neoutils.finsight.ui.screen.backup.BackupViewModel]; everything that lands in the
 * destination is here.
 *
 * **Two intents, one pipeline.** [captureIfNeeded] is the occasion nobody chose and
 * [captureNow] the copy somebody asked for; they differ in one comparison and in nothing
 * else, which is why the comparison lives here rather than at either call site. No caller
 * reasons about design D8 — a `captureNow` written in a view model would be a second place
 * that rule is decided.
 *
 * **Retention hangs off a capture that landed, and cannot be reached any other way**
 * (design D10). The sweep is private, and the only call to it sits on the right-hand side
 * of the copy having arrived in the destination — so there is no moment, and no caller,
 * that can empty a destination without first having filled it. A failed capture removes
 * nothing because there is no code path in which it could.
 *
 * The flow is the one the manual export already uses and for the same reasons: capture
 * into a file of this app's own, because `VACUUM INTO` only writes to a path and on two
 * platforms the destination is not one; hand it over; remove the temporary whatever
 * happened, under [NonCancellable], because a suspending call in a `finally` does not run
 * once its coroutine is cancelled.
 */
class BackupVault(
    private val vault: BackupVaultRepository,
    private val archive: ArchiveMark,
    private val destination: BackupDestination,
    private val database: AppDatabase,
    private val origin: CaptureOrigin,
    private val files: BackupFileService,
    private val clock: Clock,
) {

    /**
     * Takes a copy, if the vault is on and the archive has gone past the copy it already
     * has.
     *
     * Nothing here asks *why* it was called. The three triggers differ in when they fire,
     * never in what they are allowed to do, so a caller states an occasion and this states
     * the outcome — which is also why an origin is not written into the file in this
     * delivery (design D9).
     */
    suspend fun captureIfNeeded(): CaptureOutcome {
        val state = vault.observe().value
        if (!state.isOn) return CaptureOutcome.VaultOff

        val mark = markOrNull()
        if (state.covers(mark)) return CaptureOutcome.AlreadyCovered

        return files.newCapturePath().fold(
            ifLeft = { CaptureOutcome.Failed(it) },
            ifRight = { path -> captureInto(path, state, mark) },
        )
    }

    /**
     * Takes a copy because somebody asked for one, if the vault is on.
     *
     * It is [captureIfNeeded] without the single comparison that makes an unasked-for
     * capture bearable: whether the copy already in the destination still holds everything
     * the archive does (design D8). That precondition exists so a run of twenty deletions
     * does not leave twenty identical files, and it is right for an occasion nobody chose.
     * On a control somebody has just pressed it is wrong twice over. A press that produces
     * nothing reads as a broken button — and the sentence that would explain it is not even
     * true: two tables of this schema issue no generated key, so adding a currency or
     * changing which categories a budget covers moves the archive without moving the mark
     * ([ArchiveMark]). "Nothing has changed" would be false in cases anybody can reach, and
     * a redundant copy costs one file where a false sentence costs the control.
     *
     * Everything else is the same call, deliberately: the same switch, read first and here
     * (design D1), the same destination, the same recording of what covers what, and the
     * same sweep behind a copy that landed (design D10). A copy taken by hand is a copy like
     * the others — retention counts it, and it becomes the one the archive corresponds to,
     * because it is.
     *
     * **The switch is still read, and it is not a formality.** The screen that offers this
     * only appears while the vault is on, but reachability is a fact about today's
     * navigation and design D1 is a property of the vault. This never answers
     * [CaptureOutcome.AlreadyCovered].
     */
    suspend fun captureNow(): CaptureOutcome {
        val state = vault.observe().value
        if (!state.isOn) return CaptureOutcome.VaultOff

        return files.newCapturePath().fold(
            ifLeft = { CaptureOutcome.Failed(it) },
            ifRight = { path -> captureInto(path, state, markOrNull()) },
        )
    }

    /**
     * Declares that the archive is no longer the one any copy was taken from, so that none
     * is treated as covering it.
     *
     * Replacing the archive with a file's content is the one change to it that leaves
     * nothing covered while adding nothing. [ArchiveMark] counts the ids the archive has
     * issued and a restore issues none — it writes back rows a file already carried — so
     * the mark reports that nothing happened while the archive became a different archive
     * altogether. No reading of the archive expresses that, because what stopped being true
     * is not about its size. Whoever replaces it is therefore what says so, here, and the
     * next trigger of any kind takes the copy the new archive has never had.
     *
     * The instant of the last capture is untouched, because it has not stopped being true:
     * a copy was taken then, it is still the last one that succeeded, and it is the line the
     * screen shows.
     *
     * Which copy the archive *came from* is dropped here too, and put back by
     * [archiveRestoredFrom] once the replacement has landed. The two facts are different —
     * see [ArchiveCopy] — but they stop being known at the same instant, and naming a copy
     * the archive did not come from is the one thing a mark on the list must never do.
     */
    fun archiveReplaced() = vault.forgetCoverage()

    /**
     * Declares that the archive is now the content of [copy], or of a file no kept copy
     * describes when it is null — the file the user picked from a device.
     *
     * Called only once the replacement has actually landed, which is what makes an
     * unmarked list mean *unknown* rather than *wrong*. Coverage is deliberately left
     * given up: the archive the person is standing on is not the archive any copy was
     * taken from, and the next trigger must still take one.
     */
    fun archiveRestoredFrom(copy: StoredBackup?) =
        vault.recordArchiveCopy(copy?.asArchiveCopy())

    /**
     * Declares that [copy] has been taken out of the destination by this app.
     *
     * Coverage is a claim about a file, and it cannot outlive the file. A copy that is no
     * longer there holds nothing, so a mark left standing on it answers *already covered*
     * for an archive nothing is behind — and every trigger then writes nothing at all until
     * an unrelated insert happens to move the archive past it. Somebody who removes their
     * only copy would be unprotected and told nothing. Ending it here costs at most one
     * file, which is the direction this precondition is allowed to be wrong in (design D8).
     *
     * Only the copy the state is about is of any consequence: removing any other one leaves
     * the copy that covers where it was, still holding everything the archive does, and
     * nothing here moves.
     *
     * **It is said by whoever removed the file, and never gone looking for.** No reading of
     * the archive says a file has left the folder, and a vault that listed the destination
     * to check on its own copy before every decision would be answering a question its
     * caller already knows the answer to (see [ArchiveCopy]). The consequence is the one
     * design D9 accepts everywhere else: a copy that leaves by another hand — a file
     * manager, another install sweeping the same folder — is not this, and the mark stands
     * until something is added.
     */
    fun copyRemoved(copy: StoredBackup) {
        if (vault.observe().value.archiveCopy?.describes(copy) != true) return
        vault.forgetCoverage()
    }

    /**
     * Writes the archive to [path], hands the file to the destination, and — only once the
     * copy is in — records the capture and sweeps.
     *
     * The mark recorded is the one read *before* the capture, not after. A row entered
     * between the two would then be described by a mark lower than the file actually
     * holds, which costs one extra copy later and never a missing one; the other order
     * would record a mark the file does not cover and leave that row unprotected until the
     * next.
     */
    private suspend fun captureInto(
        path: String,
        state: VaultState,
        mark: Long?,
    ): CaptureOutcome {
        val at = clock.now()

        val landed: Either<BackupError, StoredBackup> = try {
            database.captureInto(
                destinationPath = path,
                appVersion = origin.appVersion,
                platform = origin.platform.id,
            )
            destination.put(capturedPath = path, name = backupFileName(at.local()))
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: DatabaseCaptureException) {
            cause.error.toBackupError().left()
        } catch (cause: Exception) {
            BackupError.EXPORT_FAILED.left()
        } finally {
            withContext(NonCancellable) { files.discard(path) }
        }

        return landed.fold(
            ifLeft = { CaptureOutcome.Failed(it) },
            ifRight = { copy ->
                // The copy that landed, not the name it was asked for: a destination may
                // have written a name of its own, and what is recorded has to be what the
                // next listing answers with.
                vault.recordCapture(at = at, mark = mark, copy = copy.asArchiveCopy())
                sweep(state)
                CaptureOutcome.Captured(copy)
            },
        )
    }

    /**
     * Removes the copies past the limit in force, oldest first, and only ever the copies
     * this vault takes on a schedule.
     *
     * The copy taken before a migration is not in the count and is never removed here
     * (design D10): the damage it exists to undo is a migration that finished without an
     * error and wrote something wrong, which is found out days later — exactly the span in
     * which the periodic captures would otherwise have carried it away. It is replaced
     * by the next migration's copy and by nothing else, which is why it is recognised by
     * carrying the one name reserved for it rather than a dated one.
     *
     * A destination that cannot be listed loses nothing. The capture still happened and is
     * still reported as such — retention is upkeep, and failing at it is not a reason to
     * tell somebody their backup did not work.
     *
     * What it removes it declares removed, through [copyRemoved] like any other removal
     * this app makes. Nothing here can reach the copy that covers — it is the one just
     * captured, so it is the newest, and the smallest limit on offer is five — but that is
     * an argument about ordering rather than a rule, and a sweep that ever did reach it
     * would otherwise leave a mark on a file it had itself deleted.
     */
    private suspend fun sweep(state: VaultState) {
        val keep = state.copiesKept() ?: return
        val copies = destination.list().getOrNull() ?: return

        copies.asSequence()
            .filterNot { it.name == PRE_MIGRATION_BACKUP_NAME }
            .drop(keep)
            .forEach { copy ->
                if (destination.remove(copy).getOrNull() == true) copyRemoved(copy)
            }
    }

    /**
     * Whether the copy this state describes still holds everything the archive does.
     *
     * Coverage is the archive standing exactly where the copy left it, which is why the
     * comparison is equality rather than *not past*. A mark that has risen is rows the copy
     * does not hold; a mark that has fallen is not this archive at all, and a copy of some
     * other archive covers nothing — answering no there costs one file, after which the two
     * agree again.
     *
     * Both unknowns answer no, and both for the same reason — that a copy too many costs a
     * file and a copy too few costs the entries nobody typed twice. A vault that has never
     * captured has nothing that could cover anything; a mark that could not be read has
     * proven nothing about the archive, and skipping on the strength of a reading that
     * never happened is the one mistake this precondition must not make.
     */
    private fun VaultState.covers(mark: Long?): Boolean {
        val taken = markAtLastCapture ?: return false
        return mark != null && mark == taken
    }

    private suspend fun markOrNull(): Long? = try {
        archive.current()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        null
    }

    private fun Instant.local() = toLocalDateTime(TimeZone.currentSystemDefault())
}
