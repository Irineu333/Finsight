package com.neoutils.finsight.ui.screen.backupHistory

import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup

/** What the copies screen asks for. */
sealed interface BackupHistoryAction {

    /** Read the destination again — on opening, and after anything that changed it. */
    data object Refresh : BackupHistoryAction

    /**
     * Read this one copy, because its sheet has just been opened.
     *
     * It is the tap and not the listing that opens a file, and the sheet is put up without
     * waiting for it: what a copy holds is inside the copy, and the answer arrives when it
     * arrives.
     */
    data class Inspect(val backup: StoredBackup) : BackupHistoryAction

    /** Replace the archive with this copy's content. */
    data class Restore(val backup: StoredBackup) : BackupHistoryAction

    /**
     * Hand this copy to a place the user picks, exactly as it is.
     *
     * It carries a [PlatformContext] for the reason the export does: the save dialog is the
     * platform's own and needs the window, the activity or the view controller to come up
     * over. It is never held.
     */
    data class Share(
        val backup: StoredBackup,
        val context: PlatformContext,
    ) : BackupHistoryAction

    data class Remove(val backup: StoredBackup) : BackupHistoryAction

    /** The user answered the restore confirmation. */
    data object ConfirmRestore : BackupHistoryAction

    /** The confirmation was dismissed without an answer. */
    data object DiscardCandidate : BackupHistoryAction

    /** No copy could be taken before the replacement, and the user said to go on anyway. */
    data object RestoreWithoutCopy : BackupHistoryAction

    /** The same question, answered by leaving the archive alone. */
    data object AbandonRestore : BackupHistoryAction
}
