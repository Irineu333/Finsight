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
 * **The rung moves only because somebody said so.** A link that has fallen is reported and
 * never acted upon: nothing here switches the vault back to the app's own storage on its
 * own, because that would move somebody's backups to another place without a word, and the
 * design says the app *asks* (design D12). Task 11.8 is what puts the question and what
 * writes provisionally inside the app while it goes unanswered; until it exists, a fallen
 * link is a sentence on the screen and captures that fail — which is exactly what the
 * instant of the last successful copy is on the screen to reveal.
 *
 * **A folder once pointed at is never forgotten.** Moving back to the app's own storage
 * leaves the remembered folder remembered, because the copies in it are still there and
 * pointing at it again is how they are found (design D4). Forgetting would be the app
 * throwing away the only thing that leads back to an archive it does not hold.
 *
 * **Changing rung copies nothing and removes nothing.** The copies on the rung left behind
 * stay where they are, unlisted and unswept but intact; carrying them across is task 11.10,
 * which design D13 says copies and never moves. The order here is what keeps that safe: the
 * preference moves only after a folder has actually been pointed at, so a picker somebody
 * closed leaves the vault exactly as it was.
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
