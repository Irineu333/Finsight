package com.neoutils.finsight.ui.screen.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext

/**
 * Pointing at a folder, which is one machine used at three moments (design D4): choosing
 * one, checking on opening that it is still there, and pointing at it again.
 *
 * The three are the same machine because **the files survive and the link does not**. On
 * Android the persisted permission goes with the package, on iOS the bookmark dies with the
 * sandbox, and on every platform a folder can be moved, renamed, unmounted or deleted. So
 * "set this up", "reconnect this" and "find my history again after reinstalling" are one
 * picker and one act of remembering, not three flows — and the third is the most valuable
 * thing this feature does, because it is reached by somebody who has just lost everything.
 *
 * **Nothing here hands out a path, or anything else that could open the folder** (design
 * D2). What a caller can learn is [FolderLink] — three words about the link — and what it
 * can do is [point] at a folder or ask. Where the copies actually go is
 * [BackupDestination]'s, and each platform's implementation of that is what holds the tree
 * `Uri`, the security-scoped `NSURL` or the path, privately.
 *
 * **It is not the destination, and the split is deliberate.** A destination is asked to
 * write, list and remove with nobody in front of the screen; this is the one part of the
 * folder rung that needs a person, once. Keeping them apart is what lets the vault run
 * against a folder without ever being able to raise a picker.
 */
interface BackupFolder {

    /**
     * Whether this platform can put a folder picker up at all.
     *
     * It is a fact about what has been built, not a judgement about the folder somebody
     * might choose — nothing here inspects a provider or separates cloud from local
     * (design D16). The screen reads it to decide whether the choice is offered, so a
     * platform whose picker is not written yet shows no control instead of a dead one.
     */
    val isOffered: Boolean

    /**
     * Puts the platform's folder picker up, remembers what was pointed at, and answers
     * whether a folder was chosen.
     *
     * False is *chose nothing* and sits on the right of the [Either], because somebody who
     * closes a picker has not hit an error and has nothing to be told — the same shape
     * [BackupFileService] gives the two dialogs it raises.
     *
     * The app's own subfolder ([BACKUP_FOLDER_NAME]) is made here, once, at the moment
     * there is a person to report a failure to. It is deliberately not made on the way
     * into a write: a folder that has gone away must fail rather than be built again
     * somewhere it never was (design D9).
     */
    suspend fun point(context: PlatformContext): Either<BackupError, Boolean>

    /**
     * What the remembered folder is, right now — asked when the app opens rather than only
     * when something is written, so a link that has fallen is noticed before a capture
     * quietly fails (design D12).
     *
     * It is a reading and never a record: the answer is about the folder as the file system
     * describes it at the instant of the call.
     */
    suspend fun link(): FolderLink
}

/**
 * The three states of the link, and only these three.
 *
 * [BROKEN] is apart from [NONE] because they lead to different offers: nothing was ever
 * pointed at, against *a folder was pointed at and cannot be reached* — which is where the
 * copies already written still are, and is the reason the way out of it is to point at the
 * same folder again rather than to start over.
 */
enum class FolderLink {

    /** Nothing has been pointed at. */
    NONE,

    /** A folder was pointed at and the app can reach it. */
    LINKED,

    /** A folder was pointed at and the app cannot reach it. */
    BROKEN,
}

/**
 * The app's own subfolder inside the folder somebody chose.
 *
 * It is here, in common code, because it has to be the same word on all three platforms:
 * finding the history again after a reinstall is pointing at the same folder and reading
 * the same subfolder of it (design D4), and a platform that spelled it differently would
 * answer "no copies" over an archive that is right there.
 *
 * The subfolder exists so that retention never goes near a file of the user's and a
 * listing never sweeps a whole documents folder.
 */
const val BACKUP_FOLDER_NAME = "Finsight backups"

/**
 * The seam where a platform's folder picker is not written yet: it offers nothing, points
 * at nothing and is linked to nothing.
 *
 * iOS binds this, alone, until its own picker is written (tasks 11.4–11.5). It is not a
 * stub standing in for behaviour — [isOffered] is false, so the choice is never put to
 * anybody there, and [BackupDestination]'s folder half cannot be reached because nothing
 * can move the vault onto it.
 */
object NoBackupFolder : BackupFolder {

    override val isOffered = false

    override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> =
        false.right()

    override suspend fun link(): FolderLink = FolderLink.NONE
}
