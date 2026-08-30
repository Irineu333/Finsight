@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backupHistory

import com.neoutils.finsight.domain.restore.RestoreConfirmation
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

    /** Newest first, as the destination answers and as retention counts. */
    val copies: List<StoredBackup> = emptyList(),

    val totalBytes: Long = 0,

    /** The copy an operation is running on, so its row alone says it is busy. */
    val working: StoredBackup? = null,

    val confirmation: RestoreConfirmation? = null,

    val captureRefusal: UiText? = null,

    val isRestoring: Boolean = false,
) {

    /**
     * One flag for every row: a restore holds the database's writer connection, and a
     * second operation started beside it would only produce one that has to wait.
     */
    val isBusy: Boolean get() = working != null
}
