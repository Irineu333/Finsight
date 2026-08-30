package com.neoutils.finsight.ui.screen.backup

import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.extension.PlatformContext

/**
 * What the screen asks for.
 *
 * Two of them carry a [PlatformContext], which no other action in this app does. Picking
 * a file is the platform's own dialog and it needs the window, the activity or the view
 * controller to come up over; the order of the work around it — capture, verify, replace,
 * and remove the temporary file whatever happened — is one flow and belongs to one place.
 * The context travels with the call that needs it and is never held: a view model that
 * kept a handle on the screen's window would outlive it.
 */
sealed interface BackupAction {

    data class Export(val context: PlatformContext) : BackupAction

    data class ChooseFileToRestore(val context: PlatformContext) : BackupAction

    /** The user answered the confirmation. */
    data object Restore : BackupAction

    /** The confirmation was dismissed without an answer. */
    data object DiscardCandidate : BackupAction

    /**
     * The user was told the copy owed before the restore could not be taken, and said to
     * restore anyway.
     */
    data object RestoreWithoutCopy : BackupAction

    /** The same question, answered by leaving the archive alone. */
    data object AbandonRestore : BackupAction

    /**
     * The one switch. It governs every trigger the vault has, so there is nothing else to
     * turn on beside it (design D1).
     */
    data class SetVaultOn(val isOn: Boolean) : BackupAction

    /** The trigger that fires when the app is opened, on its own. */
    data class SetPeriodicOn(val isOn: Boolean) : BackupAction

    /** The trigger that fires before something is destroyed, on its own. */
    data class SetPreventiveOn(val isOn: Boolean) : BackupAction

    data class SetInterval(val interval: VaultInterval) : BackupAction

    data class SetRetention(val retention: BackupRetention) : BackupAction
}
