package com.neoutils.finsight.ui.screen.backup

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
}
