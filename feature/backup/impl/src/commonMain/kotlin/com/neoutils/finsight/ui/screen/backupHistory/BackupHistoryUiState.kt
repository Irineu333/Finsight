@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backupHistory

import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.vault.ArchiveCopy
import com.neoutils.finsight.domain.vault.KeptCopyFacts
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

    /** Whether copies will go on arriving, which is what the empty state has to say. */
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
     * What the copy whose sheet is up holds, read from the file itself when it was tapped.
     *
     * One copy, never the list: opening a file is what this costs, and a listing that read
     * every copy would open one file per row (design D9). It starts over at
     * [KeptCopyFacts.Reading] on every tap, so a sheet never shows the copy before it.
     */
    val facts: KeptCopyFacts = KeptCopyFacts.Reading,

    val confirmation: RestoreConfirmation? = null,

    val captureRefusal: UiText? = null,

    val isRestoring: Boolean = false,
) {

    /**
     * One flag for every row: a restore holds the database's writer connection, and a
     * second operation started beside it would only produce one that has to wait.
     */
    val isBusy: Boolean get() = working != null

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
