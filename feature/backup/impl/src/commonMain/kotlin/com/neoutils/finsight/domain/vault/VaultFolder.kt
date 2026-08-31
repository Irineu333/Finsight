package com.neoutils.finsight.domain.vault

import arrow.core.Either
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderIdentity
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The folder rung as the app uses it: the one place a folder is pointed at, the one place
 * the link is checked, and the one place the vault is moved between the two rungs.
 *
 * It is design D4's single machine with the vault's own state attached to it. [BackupFolder]
 * knows how to raise a picker and how to answer whether a folder is reachable; what is here
 * is everything that follows from an answer — which rung the copies go to, and what the
 * screen is told about the link.
 *
 * **The choice moves only because somebody said so.** A link that has fallen is reported
 * and never written down: nothing here changes the preference on its own, because that would
 * decide on somebody's behalf where their backups live, and the design says the app *asks*
 * (design D12). What a fallen link does move is [rung] — the copies go on being taken inside
 * the app while the question stands, provisionally and saying so — and that is a derivation
 * of the same two values rather than a second record of them (see [VaultRung]).
 *
 * **A folder once pointed at is never forgotten.** Moving back to the app's own storage
 * leaves the remembered folder remembered, because the copies in it are still there and
 * pointing at it again is how they are found (design D4). Forgetting would be the app
 * throwing away the only thing that leads back to an archive it does not hold.
 *
 * **Changing rung removes nothing, here or anywhere.** The copies on the rung left behind
 * stay exactly where they are; carrying a set of them across is
 * [VaultMigration]'s, which copies and never moves (design D13), and it is offered rather
 * than performed. The order here is what keeps that safe: the preference moves only after a
 * folder has actually been pointed at, so a picker somebody closed leaves the vault exactly
 * as it was.
 *
 * **What a change of rung does end is coverage.** [VaultState.markAtLastCapture] says a copy
 * holds everything the archive holds, and a copy is somewhere: once the copies start landing
 * on the other rung, that file is in the rung left behind, and everything reading the claim
 * is reading it about the rung now in force. The preventive trigger would take no copy before
 * a restore because one already covers, and the confirmation would promise a way back through
 * a screen that lists only the rung in force — a sentence said over somebody's whole archive,
 * a moment before it is replaced. So the claim ends where its place does, here, in the one
 * place both halves of [rung] move.
 *
 * It costs at most one copy, which is the direction design D8's precondition is allowed to be
 * wrong in, and it is the movement that ends it rather than the preference: a folder that
 * fell away and a folder that came back both move the copies without anybody choosing to
 * (design D12), and answering *keep them inside the app* while the folder is already
 * unreachable moves nothing and ends nothing.
 */
class VaultFolder(
    private val state: BackupVaultRepository,
    private val folder: BackupFolder,
) {

    /** Whether this platform can put a folder picker up at all — [BackupFolder.isOffered]. */
    val isOffered: Boolean get() = folder.isOffered

    private val _link = MutableStateFlow(FolderLink.NONE)

    /**
     * What the last reading said about the link, for a screen to show.
     *
     * It starts at [FolderLink.NONE] and is a reading rather than a record: nothing is
     * claimed about a folder until [check] or [pointAt] has actually asked, which is why
     * a screen must not read *not linked* out of it before either has run.
     */
    val link: StateFlow<FolderLink> = _link

    /**
     * Where the copies are going right now — the choice, and the reading taken against it.
     *
     * It is read at the moment it is asked and never held, for the reason
     * [VaultDestinations] reads the rung per operation: both halves of it move while the app
     * is running, and an answer resolved once is an answer that goes on being given after it
     * has stopped being true.
     */
    val rung: VaultRung get() = VaultRung(state.observe().value.destination, _link.value)

    /**
     * [rung]'s [VaultRung.inForce], with which folder that is when it is
     * [VaultDestination.USER_FOLDER] — see [VaultLocation]. It is read fresh for the same
     * reason [rung] is: this is the one comparison that can tell a folder traded for another
     * apart from a folder merely reported on again, and a value held past the call that
     * changes it could no longer do that.
     */
    val location: VaultLocation
        get() {
            val inForce = rung.inForce
            return VaultLocation(
                destination = inForce,
                folder = if (inForce == VaultDestination.USER_FOLDER) folder.identity else null,
            )
        }

    /**
     * The folder's own name, for a screen to show — never a path, and never anything that
     * could reopen it (design D2; see [BackupFolder.displayName] for what each platform may
     * answer with).
     *
     * Null on the same terms [location] leaves [VaultLocation.folder] null: while
     * [VaultRung.inForce] is [VaultDestination.APP_STORAGE] the question does not apply,
     * since that rung has no name of its own to give. It is asked fresh rather than cached,
     * for the same reason
     * every other read here is: a name held past the call that moves the rung is a name that
     * can go on describing a folder no longer in force.
     */
    suspend fun displayName(): String? =
        if (rung.inForce == VaultDestination.USER_FOLDER) folder.displayName() else null

    /**
     * Asks whether the folder is still reachable, and publishes the answer.
     *
     * This is the second of design D4's three moments, and task 11.7: it runs when the app
     * opens rather than only when something is written, so that a folder which has been
     * deleted, unmounted or renamed is noticed then instead of days later, through a
     * capture that failed with nobody watching.
     *
     * It is asked whichever rung is in force. A folder that was pointed at is a fact about
     * this install whether or not the vault is currently writing to it, and it is what the
     * way back to those copies is made of.
     *
     * **The comparison stays [VaultDestination], deliberately, and not [VaultLocation].**
     * Nothing here ever points at a folder — the same one is only ever re-read — so the
     * physical place cannot have changed; what can is only whether it answers. Comparing by
     * [FolderIdentity] here as well would cost nothing on Android or the desktop and would
     * cost something real on iOS: [BackupFolder]'s own iOS implementation rewrites the
     * bookmark, in place, exactly where this reading can trigger it (a resolution that comes
     * back stale), so an identity taken before and after could disagree about a folder that
     * never moved and end a coverage nothing here earned ending.
     */
    suspend fun check() {
        val before = rung.inForce
        _link.value = folder.link()
        endCoverageIfMoved(before)
    }

    /**
     * Puts the picker up and, if a folder was chosen, moves the vault onto it.
     *
     * The first and third of design D4's three moments are this one call: choosing a folder
     * for the first time and pointing at the same one again after a reinstall differ in
     * nothing the app can see, and must not, because the second is what makes an archive
     * that outlived the app findable.
     *
     * Answers what was chosen: false for a picker somebody closed, which is not a failure
     * and changes nothing. The preference is written only on a true, and after the folder
     * has been prepared — a vault pointed at a folder it could not open would be a vault
     * that stops writing at the next trigger.
     *
     * **This is the one call that compares by [VaultLocation], and the only one that needs
     * to.** It is the sole place a *different* folder can become the one in force — every
     * other move is between here and the app's own storage, where [VaultDestination] alone
     * already says everything there is to say. A rung that reads [VaultDestination.USER_FOLDER]
     * both before and after this call used to read as unmoved regardless of which folder
     * either one names, which is the whole of what let pointing at a second folder leave a
     * stale coverage standing over an archive the new one has never seen.
     */
    suspend fun pointAt(context: PlatformContext): Either<BackupError, Boolean> {
        val before = location

        return folder.point(context).onRight { chosen ->
            if (chosen) {
                state.setDestination(VaultDestination.USER_FOLDER)
                _link.value = folder.link()
                endCoverageIfFolderChanged(before)
            }
        }
    }

    /**
     * Moves the vault back to the app's own storage.
     *
     * Nothing is removed and nothing is forgotten: the copies in the folder stay in it, the
     * folder stays remembered, and [pointAt] over the same folder brings the vault back to
     * them. It is the reverse of a choice rather than the undoing of one.
     */
    fun keepInsideApp() {
        val before = rung.inForce
        state.setDestination(VaultDestination.APP_STORAGE)
        endCoverageIfMoved(before)
    }

    /**
     * Drops the folder [pointAt] most recently shifted aside, once nothing is owed to it any
     * more (task 11.10; see [com.neoutils.finsight.domain.vault.VaultDestinationChange]).
     *
     * It never touches what is currently pointed at — [folder] answers for that alone — and
     * it costs nothing to call when nothing was shifted aside in the first place.
     */
    fun forgetPreviousFolder() = folder.forgetPrevious()

    /**
     * Gives the coverage up when the copies have started landing somewhere else.
     *
     * It is the movement of [VaultRung.inForce] and not of the preference, because the two
     * part company exactly where this matters: a folder that stops answering sends the copies
     * inside the app with the choice untouched, and a folder that answers again takes them
     * back. Both are a copy that covers being left on a rung nothing is reading any more.
     *
     * What is given up is the claim, never the capture: the instant a copy was taken stands,
     * because it has not stopped being true ([BackupVaultRepository.forgetCoverage]).
     */
    private fun endCoverageIfMoved(before: VaultDestination) {
        if (rung.inForce != before) state.forgetCoverage()
    }

    /** [endCoverageIfMoved]'s counterpart for [pointAt] — see its own comment for why. */
    private fun endCoverageIfFolderChanged(before: VaultLocation) {
        if (location != before) state.forgetCoverage()
    }
}
