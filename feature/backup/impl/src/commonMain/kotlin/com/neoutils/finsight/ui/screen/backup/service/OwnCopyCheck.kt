package com.neoutils.finsight.ui.screen.backup.service

import com.neoutils.finsight.database.snapshot.CandidateRejection
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import kotlin.coroutines.cancellation.CancellationException

/**
 * Whether a file is a copy this app wrote, decided by reading it.
 *
 * It exists because a destination may be a folder of the user's own, and the app removes
 * only what it put there (design D9). The name is what picks a file out of the folder
 * cheaply; this is what settles it, and there is no second verifier — the gate the restore
 * flow runs is the gate here, so a file this app would take back is a file this app may
 * remove, by construction rather than by two definitions kept in step.
 *
 * **It is only ever asked about a file that is already going to be removed**, and that is
 * what makes it sound to run: [CandidateVerifier.verify] migrates what it is handed, and
 * its contract is that the path must be a copy the caller is willing to lose. A removal
 * satisfies that condition exactly. Asking it about a copy the app means to keep would not.
 */
class OwnCopyCheck(private val verifier: CandidateVerifier) {

    /**
     * True only when the file at [path] was proven to be this app's.
     *
     * A check that could not be carried out — a device that will not read, a disk with no
     * room for the journal a migration needs — answers false, and false is the honest word
     * for it: nothing was proven, so nothing is removed. The alternative is deleting a file
     * on the strength of a check that never ran.
     */
    suspend fun confirms(path: String): Boolean = try {
        verifier.verify(path).provesOwnCopy()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        false
    }
}

/**
 * A file this app wrote is not only a file the gate accepts, and the difference matters to
 * retention: a copy whose content stopped satisfying an invariant would otherwise be
 * unremovable forever, and the destination would fill up with the one kind of file nobody
 * can use.
 *
 * The line is what the layer that refused had already proven when it refused. Everything
 * past the migration chain has been through Room's schema identity check, which no other
 * application's database survives — so the four findings about content, and a guard that
 * aborted over what it was handed, all speak about a file of this app's own schema.
 *
 * Before that check nothing is proven. A file that is not a database, one that carries no
 * Room bookkeeping, one whose identity hash is another schema's, and one damaged past
 * reading are all left alone; so is a file declaring a version newer than this build, which
 * is evidence of a Room database and not of this one. The cost is a copy this app wrote
 * outliving its turn, and that is the right cost to pay for never removing someone else's
 * file.
 *
 * The `when` is exhaustive on purpose: a refusal added to [CandidateRejection] fails to
 * compile here until someone decides whether it proves the file is ours.
 */
private fun CandidateVerification.provesOwnCopy(): Boolean = when (this) {
    is CandidateVerification.Accepted -> true
    is CandidateVerification.Rejected -> when (reason) {
        CandidateRejection.NOT_A_DATABASE -> false
        CandidateRejection.CORRUPTED -> false
        CandidateRejection.NOT_FROM_THIS_APP -> false
        CandidateRejection.SCHEMA_TOO_NEW -> false
        CandidateRejection.SCHEMA_MISMATCH -> false
        CandidateRejection.MIGRATION_ABORTED -> true
        CandidateRejection.UNBALANCED_LEDGER -> true
        CandidateRejection.ORPHAN_DIMENSION -> true
        CandidateRejection.FOREIGN_KEY_VIOLATION -> true
        CandidateRejection.MISPLACED_DIMENSION -> true
    }
}
