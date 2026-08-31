@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backupHistory

import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.ArchiveCopy
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.util.UiText
import kotlin.time.ExperimentalTime

/**
 * What is in the destination right now, read when the screen opened.
 *
 * There is no record of it anywhere else, and that is the decision rather than an economy
 * (design D9): the backup file holds the whole archive, so a table of copies would travel
 * inside every one of them and come back in time with a restore, describing a folder it
 * had stopped being about. What is listed is what the file system answered — a copy the
 * user deleted with a file manager is simply not in it, and that is not an error.
 */
data class BackupHistoryUiState(
    val isLoading: Boolean = true,

    /** True when the destination could not be read at all, which is not the same as empty. */
    val isUnreadable: Boolean = false,

    val destination: VaultDestination = VaultDestination.APP_STORAGE,

    /**
     * [destination]'s own name, when it is a folder the platform can currently name — never
     * a path, and never anything that could reopen it (design D2; see
     * [com.neoutils.finsight.ui.screen.backup.service.BackupFolder.displayName]).
     *
     * Null while [destination] is the app's own storage, which has no name to give; null
     * while nothing has been pointed at; and null while the platform cannot currently say.
     * [com.neoutils.finsight.ui.screen.backup.destinationLabel] falls back to the rung's own
     * words in every one of those cases.
     */
    val folderName: String? = null,

    /**
     * Whether copies will go on arriving, which is what the empty state has to say — and
     * what the two controls that write into the destination are offered on (design D1).
     */
    val isVaultOn: Boolean = false,

    /**
     * Which copy the archive in use is a copy of, as the vault recorded it when that copy
     * was taken or restored from — never a reading of the archive, which carries no such
     * stamp (see [ArchiveCopy]).
     *
     * It names a copy without claiming one exists. [isCurrent] is asked only of copies
     * [copies] already holds, so a name that has left the folder marks nothing.
     */
    val archiveCopy: ArchiveCopy? = null,

    /** Newest first, as the destination answers and as retention counts. */
    val copies: List<StoredBackup> = emptyList(),

    val totalBytes: Long = 0,

    /** The copy an operation is running on, so its row alone says it is busy. */
    val working: StoredBackup? = null,

    /**
     * Whether a copy asked for from this screen is being taken right now.
     *
     * It is beside [working] rather than in it, because it is about no copy: the file it
     * will produce does not exist yet and has no row to be busy in. What says so is the
     * control that was pressed, and [isBusy] is what keeps the rest of the screen still
     * while it runs.
     */
    val isCapturing: Boolean = false,

    /**
     * Whether a file is being brought in right now — beside [isCapturing] and not folded
     * into it for the reason it is a separate control: the two produce the same kind of
     * file and are the same kind of wait, but only one of them is running, and the spinner
     * belongs on the control that was pressed.
     */
    val isImporting: Boolean = false,

    /**
     * Whether this platform can put a folder picker up at all — so the choice of where the
     * copies are kept is offered where it works and simply absent where it is not built yet,
     * rather than shown as a control that does nothing.
     *
     * It is not a judgement about folders or providers, which the app never makes
     * (design D16).
     */
    val isFolderOffered: Boolean = false,

    /**
     * The copy a removal has been asked about and not yet answered for.
     *
     * It is the copy and not a flag, because the sheet has to say *which* one — and it is
     * held here rather than passed to a sheet the row builds, for the reason
     * [confirmation] is: a modal is rendered outside the screen's tree and the state is
     * what keeps the two in step.
     */
    val pendingRemoval: StoredBackup? = null,

    val confirmation: RestoreConfirmation? = null,

    val captureRefusal: UiText? = null,

    /**
     * Whether the copy owed before *this* restore was refused — which stays true once the
     * person has said to go on without it.
     *
     * It is not [captureRefusal] with the sentence dropped. The refusal is cleared as soon as
     * the question is answered, while the confirmation is still standing behind it: read from
     * that, the sheet would go back to promising a copy while the replacement it promised for
     * ran with nothing kept. A capture that did not happen must not be presented as protection
     * (`local-backup` spec).
     */
    val copyRefused: Boolean = false,

    val isRestoring: Boolean = false,
) {

    /**
     * One flag for every row: a restore holds the database's writer connection, and a
     * second operation started beside it would only produce one that has to wait.
     *
     * A capture asked for here is one of them. It reads the whole archive and then sweeps
     * the destination, so a restore or a removal started beside it would be working on a
     * folder being rearranged underneath — and it is the same flag that stops the control
     * being pressed a second time while the first press is still running.
     *
     * An import is one of them for the second half of that reason rather than the first: it
     * writes into the same folder, through a gate that opens the file with the database's
     * own machinery, and it ends with the same re-reading of the destination.
     */
    val isBusy: Boolean get() = working != null || isCapturing || isImporting

    /**
     * Whether [copy] is the one the app is running on right now — because it was the last
     * one taken, or because the archive was restored from it.
     *
     * False for every row is a legitimate answer and not a defect: a file the user picked
     * was restored, a restore did not land, or the copy the archive came from has since
     * left the folder. The list says nothing rather than something untrue.
     */
    fun isCurrent(copy: StoredBackup): Boolean = archiveCopy?.describes(copy) == true

    /**
     * Whether [copy] is the newest one in the destination — the least a person loses by
     * choosing it.
     *
     * It is not asked of the copy that is already the current one: a row that says both is
     * a row saying the same thing twice, and *current* is the stronger of the two.
     */
    fun isNewest(copy: StoredBackup): Boolean = copy == copies.firstOrNull()
}
