package com.neoutils.finsight.domain.vault.service

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
 * **Nothing here hands out anything that could open the folder** (design D2). What a caller
 * can learn is [FolderLink] — three words about the link — and [displayPath], which is text
 * for a person to read rather than an address anything reopens from, and what it can do is
 * [point] at a folder or ask. Where the copies actually go is [BackupDestination]'s, and each
 * platform's implementation of that is what holds the tree `Uri`, the security-scoped
 * `NSURL` or the path, privately.
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
     * The copies go straight into the folder chosen here — there is no subfolder of the
     * app's own inside it. What this does before the preference is written is confirm the
     * folder can actually be listed, once, at the moment there is a person to report a
     * failure to: a vault pointed at a folder it cannot even read would be a vault that
     * stops writing at the next trigger.
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

    /**
     * Which folder is remembered right now, or null when nothing is — see [FolderIdentity]
     * for what this may and may not be used for.
     *
     * It is read off the same token [point] persists, and nothing more: a stronger answer
     * would need the folder itself read, which is [link]'s and costs a listing this must
     * not, since it is asked on every comparison a caller wants to make and not only when
     * something changed.
     */
    val identity: FolderIdentity?

    /**
     * Where the folder is, as far as this platform can say it — enough to tell one folder
     * from another folder of the same name.
     *
     * **A last segment on its own does not do that.** Somebody who keeps a `Backups` under
     * `Documents` and another on a drive reads the same word for both, and the moment it
     * matters most is the one where being sure matters most: pointing at a folder again
     * after reinstalling, with the archive on the line (design D4).
     *
     * **It is read, never used to reach anything, and that is what keeps design D2 whole.**
     * What D2 forbids is the destination being *modelled* as text and reopened from it — on
     * iOS the grant lives inside the url object and does not survive the round trip. Text
     * put on a screen makes no such trip: every platform here goes on addressing its folder
     * through the token it already holds, and nothing reads this back.
     *
     * How much of the location each platform can honestly say differs, and the answer is
     * whatever that platform knows rather than a shape imposed on all three. What none of
     * them may do is answer something that is not where the copies are.
     *
     * Null when nothing is pointed at, and null when this platform cannot currently read it
     * — a reason to fall back to a generic sentence, never one to report as a refusal: a
     * caller that only wants something readable does not need the failure a listing would
     * have to explain.
     *
     * It is `suspend` because two of the three platforms cannot answer without asking the
     * file system.
     */
    suspend fun displayPath(): String?

    /**
     * Drops the folder [point] shifted aside on its last change, once nothing is owed to it
     * any more — the carry into the folder now in force landed, or the person answered no to
     * the offer (task 11.10; see
     * [com.neoutils.finsight.domain.vault.VaultDestinationChange]).
     *
     * It never touches what is currently pointed at, which stays exactly where [point] left
     * it — this is the one call that reaches backward instead.
     */
    fun forgetPrevious()
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
 * The seam where a platform's folder picker is not written yet: it offers nothing, points
 * at nothing and is linked to nothing.
 *
 * All three platforms have their own now, so what is left binding this is every test that
 * drives the vault without a folder in it. It is not a stub standing in for behaviour —
 * [isOffered] is false, so the choice is never put to anybody, and [BackupDestination]'s
 * folder half cannot be reached because nothing can move the vault onto it.
 *
 * It also stands in for a token that was never shifted aside — the default a platform gives
 * [com.neoutils.finsight.domain.vault.VaultDestinations] for the folder left behind before a
 * folder has ever changed twice: [identity] answers null exactly like a genuinely empty
 * previous slot would, so nothing spuriously matches it (task 11.10).
 */
object NoBackupFolder : BackupFolder {

    override val isOffered = false

    override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> =
        false.right()

    override suspend fun link(): FolderLink = FolderLink.NONE

    override val identity: FolderIdentity? = null

    override suspend fun displayPath(): String? = null

    override fun forgetPrevious() = Unit
}
