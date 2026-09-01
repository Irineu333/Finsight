@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.vault

import arrow.core.left
import com.neoutils.finsight.database.exception.DatabaseVerificationException
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toBackupError
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.domain.vault.service.backupFileName
import com.neoutils.finsight.extension.PlatformContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Bringing a backup file the person has somewhere else into the destination, so that it
 * becomes one of the kept copies.
 *
 * **It is not a restore, and the difference is the whole of it.** A restore replaces the
 * archive with a file's content and is offered on both screens already; this replaces
 * nothing and reads nothing back — it puts a file where the vault keeps its copies, and the
 * person decides afterwards, from the list, whether to restore it. The archive is untouched
 * by every path through here.
 *
 * **The file passes the restore's own gate before it lands** ([CandidateVerifier]). It is
 * not a courtesy check: the destination may be a folder of the person's own, and the app
 * removes only what it can prove it wrote (`OwnCopyCheck`, design D9). A file that landed
 * without being read would be litter this app is then unable to sweep, in somebody else's
 * folder — so a file the gate refuses does not land at all, and the refusal is said in the
 * words that refusal already has.
 *
 * Requiring [CandidateVerification.Accepted] rather than merely *provably ours* is the
 * stronger of the two lines and it is deliberate: the removal only needs the file to be this
 * app's, but a copy that the restore would turn away is a copy nobody can ever use. Accepted
 * implies removable, so one gate answers both.
 *
 * **The name is this device's, and never the file's.** The listing that draws the history
 * filters by the app's own naming convention, so a file copied in under its original name
 * would be invisible — an import that "succeeded" and showed nothing. The name is therefore
 * written fresh from the clock at the moment the copy arrives, which is also the instant the
 * file system will report for it: name and row agree, the copy sorts and is unique among the
 * others by exactly the property the convention exists for (`backupFileName`), and the one
 * reserved name a pre-migration copy carries cannot be reached, because no name here comes
 * from the file. What the file says about *itself* — which device wrote it, which build, and
 * when — is inside it, and the sheet that opens on the row is what reads it.
 *
 * **The name also carries that it arrived this way** (`backupFileName(imported = true)`,
 * read back by `isImportedFileName`). It is the one fact `snapshot_meta` cannot supply: the
 * stamp names a platform and a version, the same four columns whoever wrote the file, and
 * nothing in it says *this install*. A file brought in from a picker may be this install's
 * own earlier export, a copy shared from someone else's phone, or another install's capture
 * carried over from a folder more than one device writes to — indistinguishable once it is
 * sitting in the destination unless the arrival itself is remembered. A restore reads the
 * mark to keep from calling such a copy this app's own past
 * ([com.neoutils.finsight.domain.restore.RestoreSource]).
 *
 * **Nothing here records anything about the archive.** The mark that says which copy the
 * running app came from is written by a capture that landed and by a restore that landed,
 * and by nothing else ([ArchiveCopy]): an imported file is not the archive, and marking it
 * would be a lie in the one place the list is authoritative. Coverage is untouched for the
 * same reason — the archive did not move.
 *
 * **Retention counts it like any other copy**, because it is one: it carries a dated name of
 * the shape the sweep recognises, and the next capture's sweep drops it with the rest when it
 * falls past the limit (design D10). Nothing is swept here — retention hangs off a capture
 * that landed and is reachable no other way.
 *
 * The file travels through this app's own temporary area, as everything crossing that
 * boundary does: a picker hands over a copy at a path, and the destination is given a path
 * and never asked for one (design D2). The temporary is removed on every way out, under
 * [NonCancellable], because a suspending call in a `finally` does not run once its coroutine
 * is cancelled.
 */
class ArchiveImport(
    private val state: BackupVaultRepository,
    private val destination: BackupDestination,
    private val verifier: CandidateVerifier,
    private val files: BackupFileService,
    private val clock: Clock,
) {

    /**
     * Puts the file the person picks into the destination, or says why it did not go in.
     *
     * Nothing raises. Every failure it can name comes back as an outcome, so a caller
     * running in a view model scope has nothing left that could escape as a crash;
     * [CancellationException] is the one exception that keeps travelling.
     */
    suspend fun importChosenFile(context: PlatformContext): ImportOutcome {
        // Read first, before a picker is raised and before anything is written, for the
        // reason a capture reads it first: nothing lands in the vault's destination while
        // the vault is off (design D1). The control is not offered then, but reachability
        // is a fact about today's navigation and the switch is a property of the vault.
        if (!state.observe().value.isOn) return ImportOutcome.VaultOff

        val produced = try {
            files.copyInChosenFile(context)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            // Nothing here read the file — it was carried, not opened — so a failure is
            // the check never having started rather than a word about what was picked.
            BackupError.VERIFICATION_FAILED.left()
        }

        val chosen = produced.getOrNull() ?: return produced.fold(
            ifLeft = { ImportOutcome.Failed(it) },
            ifRight = { ImportOutcome.Abandoned },
        )

        return try {
            when (val verification = verifier.verify(chosen)) {
                is CandidateVerification.Accepted -> land(chosen)

                is CandidateVerification.Rejected ->
                    ImportOutcome.Failed(verification.reason.toBackupError())
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: DatabaseVerificationException) {
            ImportOutcome.Failed(cause.error.toBackupError())
        } catch (cause: Exception) {
            ImportOutcome.Failed(BackupError.VERIFICATION_FAILED)
        } finally {
            withContext(NonCancellable) { files.discard(chosen) }
        }
    }

    /**
     * Hands the approved file to the destination under a name of this device's making.
     *
     * The copy that landed is the answer, and not the name it was asked for: a destination
     * writes under a name near the one it was given when that one is taken, and on Android
     * the provider may rename the document it creates (design D9).
     */
    private suspend fun land(path: String): ImportOutcome {
        val name = backupFileName(
            at = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            imported = true,
        )

        return destination.put(capturedPath = path, name = name).fold(
            ifLeft = { ImportOutcome.Failed(it) },
            ifRight = { ImportOutcome.Imported(it) },
        )
    }
}

/**
 * What became of bringing a file in.
 *
 * Nothing here says anything about the archive, because there is nothing to say: every
 * outcome leaves it exactly as it was.
 */
sealed interface ImportOutcome {

    /** The file is in the destination, and this is the copy that landed. */
    data class Imported(val copy: StoredBackup) : ImportOutcome

    /** The picker was closed. Nothing happened, and there is nothing to say about it. */
    data object Abandoned : ImportOutcome

    /** The vault is off, so nothing may be written into its destination (design D1). */
    data object VaultOff : ImportOutcome

    /**
     * Nothing landed, and this is why — the gate's word about the file, or the machine's
     * about itself. They are one outcome because the person acts on the sentence rather
     * than on the distinction, and [BackupError] is what already draws that line.
     */
    data class Failed(val error: BackupError) : ImportOutcome
}
