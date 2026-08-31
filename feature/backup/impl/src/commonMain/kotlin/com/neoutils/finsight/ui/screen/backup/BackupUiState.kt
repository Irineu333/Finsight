@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultRung
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.util.UiText
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What the screen is doing, what the vault is, and what it is waiting for an answer about.
 *
 * The chosen file is not in it. What the user picked lives in this app's temporary area
 * until it is either used or thrown away, and a path is not something the screen renders.
 */
data class BackupUiState(
    val isExporting: Boolean = false,
    val isVerifying: Boolean = false,
    val isRestoring: Boolean = false,
    val confirmation: RestoreConfirmation? = null,
    /**
     * Why the copy owed before the restore could not be taken, while the user is being
     * asked whether to go on without it.
     *
     * A sentence rather than an error value: the refusal arrives from
     * [com.neoutils.finsight.feature.backup.api.PreventiveBackup] already worded, and there
     * is nothing left for this screen to decide from it.
     */
    val captureRefusal: UiText? = null,

    /**
     * Whether the copy owed before *this* restore was refused — which stays true once the
     * person has said to go on without it.
     *
     * It is not [captureRefusal] with the sentence dropped, and the difference is the whole
     * reason it exists. The refusal is cleared the moment the question is answered, and the
     * confirmation is still standing behind it: read from that, the sheet would go back to
     * promising a copy while the replacement it promised for ran with nothing kept. A
     * capture that did not happen must not be presented as protection (`local-backup` spec),
     * so this is cleared where the flow ends and nowhere earlier.
     */
    val copyRefused: Boolean = false,

    /** The vault as it stands, read from the one place it is kept. */
    val vault: VaultState = VaultState(),

    /**
     * The last reading of a destination, whichever one it was of — see [copiesInForce],
     * which is what the screen says anything from.
     */
    val copies: VaultCopies = VaultCopies(),

    /**
     * Whether this platform can put a folder picker up at all — so the choice of where
     * copies are kept is offered where it works and simply absent where it is not built
     * yet, rather than shown as a control that does nothing.
     *
     * It is not a judgement about folders or providers, which the app never makes
     * (design D16).
     */
    val isFolderOffered: Boolean = false,

    /**
     * What the last reading said about the folder somebody pointed at.
     *
     * Read when the app opens rather than only when something is written (task 11.7), so a
     * folder that was deleted, unmounted or renamed is noticed before a capture fails with
     * nobody watching.
     */
    val folderLink: FolderLink = FolderLink.NONE,
) {

    /**
     * Where the copies are going: what was chosen, and the reading taken against it.
     *
     * The rule that pairs the two is [VaultRung]'s and not this screen's — the router that
     * sends a copy reads the same one — so the card can never name a destination that
     * nothing is landing in.
     */
    val rung: VaultRung get() = VaultRung(vault.destination, folderLink)

    /**
     * What the destination holds, when the reading is of the rung the copies are actually
     * going to — read when the screen opens and after anything that changes it, and never a
     * record kept elsewhere (design D9).
     *
     * **A reading of another rung is no reading at all**, and dropping it is the whole
     * point. A re-read that fails leaves the last answer standing ([copies]), which is right
     * while the destination is the same one and a lie the moment it is not: the count would
     * be of the app's own storage under the name of the folder somebody has just pointed at,
     * or of a folder under the sentence saying the app cannot reach it. Both are the states
     * a fallen link and a change of destination put people in, which is exactly where the
     * screen has to be trusted.
     */
    val copiesInForce: VaultCopies
        get() = copies.takeIf { it.rung == rung.inForce } ?: VaultCopies()

    /**
     * The copy the card names as the last backup: the newest one standing where the copies
     * are going, and this install's own last capture only while that destination has not
     * answered.
     *
     * **It is the reading and not [VaultState.lastCapturedAt], because of where it is read.**
     * The instant sits between the destination's name and the count of what is in it, so an
     * instant belonging to the other rung is the card saying that a copy taken inside the app
     * is in the folder somebody has just pointed at. That is the failure [copiesInForce]
     * exists to prevent, one field over, and the reading answers it the same way: it is about
     * the place it was taken from, so it cannot be shown over another one.
     *
     * It is also the only one of the two that a folder emptied from a file manager moves. The
     * vault's instant is a fact about this install that no listing can contradict, and the
     * card's line is a claim about what is there to come back to.
     *
     * **A destination that has not answered leaves the vault's instant standing**, which is
     * what the card said before any reading landed and is true of the install if not of the
     * folder. It is replaced the moment the listing arrives, and there is nothing else to put
     * there: a card that said *none yet* over a destination it has not read would be design
     * D9's forbidden sentence, moved up one line.
     */
    val lastCopyAt: Instant?
        get() = if (copiesInForce.isRead) copiesInForce.newestAt else vault.lastCapturedAt

    /**
     * One flag for both entries: each of the three operations has the database's writer
     * connection to itself, and offering the other while one runs would only produce a
     * second one that has to wait.
     */
    val isBusy: Boolean get() = isExporting || isVerifying || isRestoring
}

/**
 * The destination as a line of text: how many copies are in it, how much room they take,
 * and when the newest one landed.
 *
 * The newest one's instant is the file system's and not the vault's
 * [VaultState.lastCapturedAt]: the two answer different questions — one is what is there
 * now, the other is when this install last succeeded — and they part company exactly where
 * it matters, when somebody removes files from outside the app.
 */
data class VaultCopies(
    /**
     * Which rung this is a reading of, or null when nothing has been read.
     *
     * Null is what separates *nothing was read* from *nothing is there*, and without it
     * every line built from this asserts an empty folder from the moment the screen opens —
     * including on a destination the app never managed to list. Nothing says "no copies yet"
     * until a listing has landed.
     *
     * Naming the rung is the other half of the same guard: a reading is about the place it
     * was taken from, and the place changes under it — somebody points at a folder, or the
     * link to one falls and the copies start going inside the app. See
     * [BackupUiState.copiesInForce].
     */
    val rung: VaultDestination? = null,
    val count: Int = 0,
    val totalBytes: Long = 0,
    val newestAt: Instant? = null,
) {

    /** Whether a destination has actually been read. */
    val isRead: Boolean get() = rung != null
}
