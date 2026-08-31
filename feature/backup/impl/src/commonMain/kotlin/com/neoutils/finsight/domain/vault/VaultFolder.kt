package com.neoutils.finsight.domain.vault

import arrow.core.Either
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
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
     */
    suspend fun check() {
        _link.value = folder.link()
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
     */
    suspend fun pointAt(context: PlatformContext): Either<BackupError, Boolean> =
        folder.point(context).onRight { chosen ->
            if (chosen) {
                state.setDestination(VaultDestination.USER_FOLDER)
                _link.value = folder.link()
            }
        }

    /**
     * Moves the vault back to the app's own storage.
     *
     * Nothing is removed and nothing is forgotten: the copies in the folder stay in it, the
     * folder stays remembered, and [pointAt] over the same folder brings the vault back to
     * them. It is the reverse of a choice rather than the undoing of one.
     */
    fun keepInsideApp() = state.setDestination(VaultDestination.APP_STORAGE)
}
