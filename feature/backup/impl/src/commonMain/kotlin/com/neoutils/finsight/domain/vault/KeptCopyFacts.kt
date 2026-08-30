package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.database.snapshot.ArchiveCounts
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.domain.restore.FileOrigin
import com.neoutils.finsight.domain.restore.toFileOrigin
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * What one kept copy says about itself, for whoever has just reached for it.
 *
 * The file system knows a copy's name, its stamp and its size, and nothing else — what it
 * *holds* is inside the file, in the `snapshot_meta` the capture wrote and in the rows
 * themselves. So the three states here are not presentation: reading a copy is opening a
 * file, it takes as long as it takes, and it can fail on a copy that was listed a moment
 * ago.
 */
sealed interface KeptCopyFacts {

    /** The copy is being opened. Nothing is known yet beyond what the listing already said. */
    data object Reading : KeptCopyFacts

    /** What the file said about itself, and how much of the archive is in it. */
    data class Held(
        val origin: FileOrigin?,
        val counts: ArchiveCounts,
    ) : KeptCopyFacts

    /**
     * The copy could not be opened — it left the folder between the listing and the tap,
     * it is damaged, or it is not a file this build can read.
     *
     * It is a state and not an error to report: the listing was true when it was taken,
     * and a folder the user can reach is a folder that changes under the app (design D9).
     */
    data object Unreadable : KeptCopyFacts
}

/**
 * Opens one kept copy and reads what it holds — one file, the one that was just tapped.
 *
 * **It is never asked about a list.** A listing that read every copy would open twenty
 * files to draw a screen, and the history is the folder rather than a record beside it
 * (design D9): what the file system answers is what the list shows, and the file is opened
 * at the moment somebody reaches for one. That is also the moment the reading is worth
 * paying for — the person is deciding about *this* copy.
 *
 * **Nothing here learns a path into the destination** (design D2). The copy is written out
 * into a temporary file of this app's own, by the same [BackupDestination.copyOut] the
 * restore and the export use — a path goes in, and none ever comes out.
 *
 * **The gate is the reader.** There is no second way to open a copy and no second idea of
 * what makes one readable: [CandidateVerifier] is what the restore runs and what a removal
 * confirms with, and its answer is where these facts come from. A copy this reader cannot
 * describe is a copy the restore would refuse, which is worth knowing before the restore
 * is started rather than after.
 *
 * The temporary is removed on every way out, under [NonCancellable], because a suspending
 * call in a `finally` does not run once its coroutine is cancelled — and this one is
 * cancelled routinely, every time a second copy is tapped before the first has answered.
 */
class KeptCopyReader(
    private val destination: BackupDestination,
    private val files: BackupFileService,
    private val verifier: CandidateVerifier,
) {

    /**
     * What [backup] holds, or [KeptCopyFacts.Unreadable] when it could not be opened.
     *
     * Nothing raises. Every failure it could name — no room for the temporary, a copy that
     * is gone, a device that will not read, a file that is not what its name says — is the
     * same answer to the person: this copy cannot be described. The distinctions belong to
     * a restore, which says them one by one and is where they change what anybody does.
     */
    suspend fun read(backup: StoredBackup): KeptCopyFacts {
        val path = files.newCapturePath().getOrNull() ?: return KeptCopyFacts.Unreadable

        return try {
            when (destination.copyOut(backup, path).getOrNull()) {
                true -> verifier.verify(path).facts()
                else -> KeptCopyFacts.Unreadable
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            KeptCopyFacts.Unreadable
        } finally {
            withContext(NonCancellable) { files.discard(path) }
        }
    }
}

/**
 * A refused file describes nothing, however it was refused.
 *
 * The ten refusals are one answer here on purpose: they separate a file that may replace
 * the archive from one that may not, and this is not that question. Somebody reading a
 * sheet about a copy is owed the facts or the plain statement that there are none, and a
 * misplaced dimension is not a fact about a copy that anyone can act on.
 */
private fun CandidateVerification.facts(): KeptCopyFacts = when (this) {
    is CandidateVerification.Accepted -> KeptCopyFacts.Held(
        origin = origin?.toFileOrigin(),
        counts = counts,
    )

    is CandidateVerification.Rejected -> KeptCopyFacts.Unreadable
}
