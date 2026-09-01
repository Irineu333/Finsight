@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.vault.service

import arrow.core.Either
import com.neoutils.finsight.domain.error.BackupError
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A place this app keeps copies of the archive in, and finds its way back to on its own.
 *
 * It is the other half of [BackupFileService], and the two are opposites on the one point
 * that matters: the file service reaches the world through a picker, so every operation of
 * it needs a person in front of the screen, while everything here happens without one being
 * asked anything. That is the whole of what a destination adds — an address the app already
 * has.
 *
 * **Nothing here hands out a path, and that is a constraint rather than a preference**
 * (design D2). On iOS the folder a user points at is a security-scoped `NSURL` that carries
 * its permission inside the object, and Apple documents that converting it to text and back
 * destroys the scope; on Android it is a tree `Uri`. A destination modelled as a string is
 * not awkward on those two platforms, it is impossible. So what a caller gets back is a
 * [StoredBackup] — what the file system says about a copy, and nothing it could open.
 *
 * What still travels as a path is the app's own temporary file, and that is correct:
 * `VACUUM INTO` only knows how to write to one. The flow is unchanged — capture into a
 * temporary, hand it to the destination, remove the temporary — and [put] is the middle
 * step. The temporary stays the caller's to remove, here as everywhere else.
 */
interface BackupDestination {

    /**
     * Puts the file captured at [capturedPath] into this destination, under [name] or
     * under a name near it, and answers with the copy that landed.
     *
     * The name is asked for rather than obeyed, because it cannot be guaranteed: a
     * destination already holding that name gets a distinct one, and on Android the
     * provider is free to rename the document it creates. The answer is what the
     * destination actually wrote, which is why [put] answers a [StoredBackup] instead of
     * nothing.
     *
     * The captured file is read and not moved. Whoever captured it still owns it and
     * still removes it.
     */
    suspend fun put(capturedPath: String, name: String): Either<BackupError, StoredBackup>

    /**
     * The copies this destination holds, newest first.
     *
     * It is a reading of the destination at the moment it is asked, never a record kept
     * elsewhere (design D9): a copy the user deleted from a file manager is simply not in
     * the answer, and no error is made of it.
     *
     * Only files whose name is this app's is offered, which is a cheap filter and nothing
     * more — the name is not authority over what a file is, and [remove] settles that by
     * reading the file itself.
     */
    suspend fun list(): Either<BackupError, List<StoredBackup>>

    /**
     * Writes one of the copies [list] answered with to [destinationPath], a path in this
     * app's own area that the caller owns and removes.
     *
     * It is what lets a copy the vault took be *used*: restored through the same gate a
     * picked file goes through, or handed to a place the user chooses, without capturing a
     * second one (design D15). The copy in the destination is read and left exactly as it
     * was.
     *
     * The direction is the whole reason this does not break the rule above: a path goes
     * *in*, and none ever comes out. A folder on iOS is a security-scoped `NSURL` whose
     * permission dies on the way through a string, so a destination that could answer with
     * a path would be a destination that does not work there (design D2).
     *
     * A copy that is no longer in the destination — removed from a file manager between
     * the listing and this call — answers false. It is what the history is built to
     * tolerate, and not an error to report as one.
     */
    suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean>

    /**
     * Removes one of the copies [list] answered with, once the file has been confirmed to
     * be one this app wrote. True when it is gone, false when the destination refused to
     * touch it because its content is not this app's.
     *
     * The confirmation is the point of the operation and not a precaution: a destination
     * may be a folder the user chose, holding files of their own, and this app removes
     * only what it wrote itself (design D9). A file that is already gone is answered as
     * removed — there is nothing left to refuse.
     */
    suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean>

    /**
     * The one destination this is at this instant, for a caller that must address the same
     * one for several operations in a row.
     *
     * Almost every destination *is* one already and answers itself: a folder somebody
     * pointed at, or the app's own storage, does not become another place between two
     * calls. The exception is the router that stands for both rungs
     * ([com.neoutils.finsight.domain.vault.VaultDestinations]), which resolves the rung on
     * every operation because the choice and the folder's reachability both move while the
     * app runs — that is right for a screen listing what is there, and wrong for a sequence
     * that has to end where it began.
     *
     * **A capture is such a sequence.** It hands a file over, reads it back to check it,
     * and then sweeps what the destination now holds past the limit — and a rung that moved
     * in between would have the sweep removing copies from a place this capture put nothing
     * in. Resolving once and carrying the answer is what makes the sequence one destination
     * rather than three lookups that may disagree.
     */
    fun resolved(): BackupDestination = this
}

/**
 * One copy, as the file system describes it: what it is called, when it was written, and
 * how big it is.
 *
 * There is no path and no handle in it, by [BackupDestination]'s whole reason for
 * existing, and no reading of the copy's own content either — what a file says about
 * itself lives in its `snapshot_meta` and is read when a user reaches for it, which is
 * also the moment a confirmation needs it anyway (design D9).
 *
 * The name is how the destination finds the copy again, so it is the plain name of a file
 * inside the destination and never a path to one.
 */
data class StoredBackup(
    val name: String,
    val savedAt: Instant,
    val sizeInBytes: Long,
)

/**
 * Newest first, which is the order the history is read in and the order retention counts
 * in.
 *
 * The file system's own timestamp decides rather than the name, because the name is not
 * authority (design D9) and a provider may have altered it. What breaks a tie is the stamp
 * the name carries, worth exactly what it is worth: two copies the destination reports at
 * the same instant are ordered by the moment they were asked to carry.
 *
 * **The stamp, and not the name it sits in** — see [backupNameStamp]. The names share a
 * prefix, so comparing them raw is decided by what follows it, which for an imported copy
 * is `imported-` and outranks every date. On a destination that answers one time for every
 * file, that put imported copies above a copy taken seconds ago, in the very order
 * retention counts in. The raw name stays as the last resort, where two copies carry the
 * same stamp and something still has to decide.
 */
internal val NEWEST_FIRST: Comparator<StoredBackup> =
    compareByDescending<StoredBackup> { it.savedAt }
        .thenByDescending { backupNameStamp(it.name) }
        .thenByDescending { it.name }
