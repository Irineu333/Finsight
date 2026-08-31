@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.VaultState
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
     * What the destination holds, read when the screen opens and after anything that
     * changes it — never a record kept elsewhere (design D9).
     */
    val copies: VaultCopies = VaultCopies(),
) {

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
    val count: Int = 0,
    val totalBytes: Long = 0,
    val newestAt: Instant? = null,
)
