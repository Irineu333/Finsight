@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.restore

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseRestoreException
import com.neoutils.finsight.database.exception.DatabaseVerificationException
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.replaceContentFrom
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toBackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.util.UiText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Replacing the archive with a file's content, from the file arriving to the file being
 * gone — the one implementation of it, for both files the app can be handed.
 *
 * A file picked from the device and a copy the vault kept differ in exactly one step, how
 * the candidate arrives, so that step is the parameter and everything after it is here:
 * the gate, the question, the copy owed before the replacement, the replacement itself,
 * and the removal. Two screens offering a restore must not be two decisions about what a
 * valid candidate is, when the person is asked, or whether a failed copy may be walked
 * past.
 *
 * **A flow lasts as long as the file it made.** The candidate is not parked in a field: one
 * call copies the file in, asks about it, waits there for the answer, and replaces the
 * archive — so the removal is one `finally` over one body, and a caller that goes away
 * while the sheet is up runs the same way out as a caller whose user said no. Every removal
 * is made under [NonCancellable], because a suspending call in a `finally` does not run
 * once its coroutine is cancelled.
 *
 * **The confirmation is only ever asked about an approved file** (`local-backup` spec). The
 * gate runs first and in full, and its refusals come back as one error each; asking before
 * it has run would transfer a decision the app cannot yet stand behind.
 *
 * **The archive is copied before it is replaced, or the user says to go on without one.**
 * The vault is asked by naming the action and nothing else — never by deciding which
 * actions are worth a copy (design D7) — and a refusal is the only outcome that stops the
 * flow. It stops it by asking, because the person in front of the screen is the only one
 * who may turn a refusal into a restore with nothing behind it.
 *
 * **Nothing here raises.** Every failure it can name comes back as [RestoreOutcome.Failed],
 * so a caller running in a view model scope has nothing left that could escape as a crash;
 * [CancellationException] is the one exception that keeps travelling.
 */
class ArchiveRestore(
    private val database: AppDatabase,
    private val verifier: CandidateVerifier,
    private val preventive: PreventiveBackup,
    private val vault: BackupVault,
    private val files: BackupFileService,
) {

    /**
     * Runs the whole of it over the file [candidate] produces.
     *
     * @param candidate a copy of the file to restore from, at a path this app owns and may
     * throw away, or null when there is none to restore from — a picker the user closed,
     * or a copy that is no longer in the destination. Whatever it hands back is this
     * call's to remove from then on.
     */
    suspend fun restoreFrom(
        candidate: suspend () -> Either<BackupError, String?>,
        questions: RestoreQuestions,
    ): RestoreOutcome {
        val produced = try {
            candidate()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            // Nothing here read the file — it was carried, not opened — so a failure is
            // the check never having started rather than a word about what was picked.
            BackupError.VERIFICATION_FAILED.left()
        }

        val chosen = produced.getOrNull() ?: return produced.fold(
            ifLeft = { RestoreOutcome.Failed(it) },
            ifRight = { RestoreOutcome.Abandoned },
        )

        var unclaimed: String? = chosen

        return try {
            when (val verification = verifier.verify(chosen)) {
                is CandidateVerification.Accepted -> {
                    if (!questions.confirm(verification.toConfirmation())) {
                        RestoreOutcome.Abandoned
                    } else if (!mayReplaceArchive(questions)) {
                        RestoreOutcome.Abandoned
                    } else {
                        val error = replaceArchiveWith(chosen)
                        unclaimed = null
                        drop(chosen)
                        error?.let(RestoreOutcome::Failed) ?: RestoreOutcome.Restored
                    }
                }

                is CandidateVerification.Rejected -> {
                    RestoreOutcome.Failed(verification.reason.toBackupError())
                }
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: DatabaseVerificationException) {
            RestoreOutcome.Failed(cause.error.toBackupError())
        } catch (cause: Exception) {
            RestoreOutcome.Failed(BackupError.VERIFICATION_FAILED)
        } finally {
            unclaimed?.let { drop(it) }
        }
    }

    /**
     * Whether the replacement may go ahead: because the copy that makes it reversible was
     * taken, because none was owed, or because the user said to go on without one.
     *
     * The copy is asked for *after* the confirmation and before the replacement, which is
     * the only window in which it is worth anything. Earlier would take a file for a
     * restore the user then cancels; later would record the archive already gone, which
     * design D6 refuses to call protection.
     */
    private suspend fun mayReplaceArchive(questions: RestoreQuestions): Boolean = try {
        preventive.captureBefore(DestructiveAction.RESTORE_BACKUP)
        true
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: PreventiveCaptureException) {
        questions.permitWithoutCopy(cause.reason)
    }

    /**
     * Replaces the archive with the approved file's content, in one transaction and
     * without closing anything — the screens go on rendering, and reflect the new archive
     * when it returns. The failure it could not carry out is the answer, rather than an
     * exception, because the file has to be removed and the user told either way.
     *
     * It runs under [NonCancellable] because there is nothing to call off. The swap either
     * lands or reverts, the sheet that asked for it refuses to be dismissed while it runs,
     * and the file is attached to the app's only writer connection until it returns — a
     * screen that went away mid-replacement would otherwise have the removal below race a
     * transaction still reading from the file.
     *
     * The vault is told inside the same uninterruptible block, and for the same reason it
     * is there: a replacement that outlives the screen leaves an archive no copy describes,
     * and the telling has to outlive it too.
     *
     * **Coverage is given up first, and the order is chosen for the failure rather than for
     * the success.** The two writes have no atomicity between them and cannot have any: one
     * is a SQLite transaction, the other is a settings file, and [NonCancellable] guards a
     * coroutine being cancelled, never the process being killed. So the question is only
     * which way round is survivable in the window between them.
     *
     * Killed after the swap and before the telling, the other order leaves a mark taken
     * from an archive that no longer exists. A restore issues no ids — it writes back rows
     * a file already carried — so that stale mark can equal the restored archive's, and
     * `covers` then reports the freshly restored archive as already held by a copy of
     * something else. The protection is gone, and nothing says so.
     *
     * Killed in the same window this way round, the app has forgotten a copy that does
     * still cover the archive, and the next trigger takes one file more than it needed.
     * The same is true of a swap that simply fails: the archive is untouched, the copy
     * still describes it, and it costs a file. One direction costs a file, the other costs
     * the guarantee, and only the first is recoverable.
     */
    private suspend fun replaceArchiveWith(path: String): BackupError? = try {
        withContext(NonCancellable) {
            vault.archiveReplaced()
            database.replaceContentFrom(path)
        }
        null
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: DatabaseRestoreException) {
        cause.error.toBackupError()
    } catch (cause: Exception) {
        BackupError.RESTORE_FAILED
    }

    private suspend fun drop(path: String) {
        withContext(NonCancellable) { files.discard(path) }
    }
}

/**
 * What the two questions a restore asks are put to, and where the answers come back from.
 *
 * They are a screen's, not the flow's: each screen has its own way of putting a sheet up
 * and its own state to keep while somebody reads it. What is not a screen's is *when* they
 * are asked and what the answers mean, which is why the flow calls them rather than
 * publishing a value for a screen to react to.
 *
 * Not answering is answering no in both. Walking away from either question leaves the
 * archive exactly as it was, which is the default the person never has to choose.
 */
interface RestoreQuestions {

    /** Whether the archive may be replaced with the file the confirmation describes. */
    suspend fun confirm(confirmation: RestoreConfirmation): Boolean

    /**
     * Whether the replacement may happen with nothing kept back, now that the copy owed
     * before it could not be taken and [reason] says why.
     *
     * Only the person reading it may say yes: a capture that did not happen must not be
     * presented as protection (`local-backup` spec), and there is nobody else the question
     * could be put to.
     */
    suspend fun permitWithoutCopy(reason: UiText): Boolean
}

/** What became of a restore, as the screen that started it has to report it. */
sealed interface RestoreOutcome {

    /** The archive is the file's now. */
    data object Restored : RestoreOutcome

    /**
     * Nothing was replaced and there is nothing to say: a picker closed, a copy that was
     * no longer there, a question answered with no, or one walked away from.
     */
    data object Abandoned : RestoreOutcome

    /** The archive is exactly as it was, and this is what stopped the restore. */
    data class Failed(val error: BackupError) : RestoreOutcome
}

/**
 * The verification's word about a file, as the confirmation states it.
 *
 * The counts arrive typed by facade and are passed on as they came: which tables they were
 * counted from is `:core:database`'s business, and an entity added to the schema later is
 * not something a screen has to remember.
 */
private fun CandidateVerification.Accepted.toConfirmation() = RestoreConfirmation(
    origin = origin?.let {
        FileOrigin(
            platform = BackupPlatform.ofId(it.platform),
            platformId = it.platform,
            appVersion = it.appVersion,
            createdAt = Instant.fromEpochMilliseconds(it.createdAt),
        )
    },
    counts = counts,
)
