@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseCaptureException
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toBackupError
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.restore.ArchiveRestore
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.restore.RestoreOutcome
import com.neoutils.finsight.domain.restore.RestoreQuestions
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.backupFileName
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_export_success
import com.neoutils.finsight.resources.backup_restore_success
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.vault.PendingAnswer
import com.neoutils.finsight.ui.vault.VaultDestinationFlow
import com.neoutils.finsight.util.UiText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource

/**
 * The backup screen: the vault as a set of preferences, and the two file operations the
 * user performs by hand.
 *
 * **The vault is read, never re-decided.** Every rule it has — whether a copy is owed,
 * which actions are worth one, how many are kept — lives behind [ArchiveRestore],
 * [com.neoutils.finsight.domain.vault.BackupVault] and the classification in the domain.
 * What is here is the switch and the settings, which is a screen deciding *whether* a rule
 * applies and never *which* rule it is.
 *
 * **The restore is not implemented here.** It is [ArchiveRestore]'s, because the copies
 * screen offers the same operation over a file that arrives differently, and two screens
 * offering a restore must not be two decisions about when the person is asked or whether a
 * failed copy may be walked past. What stays here is the asking: the sheets are this
 * screen's, and so is the state that keeps them up.
 *
 * **The export goes through a file of this app's own that nothing else owns**, and removing
 * it is part of the flow rather than an afterthought: the capture writes to a temporary
 * path because it only knows how to write to one, and on two platforms the destination the
 * user picked is not a path. Leaving the screen cancels the scope it runs in, and a
 * suspending call in a `finally` does not run once its coroutine is cancelled, so the
 * removal is made under [NonCancellable].
 *
 * **A failure reaches the user rather than the crash handler.** Everything below runs in
 * [viewModelScope], where an exception that escapes is an uncaught crash and not a message,
 * so each operation catches what it can name, reports whatever else it meets as the failure
 * of the operation that met it, and rethrows [CancellationException].
 */
class BackupViewModel(
    private val database: AppDatabase,
    private val archiveRestore: ArchiveRestore,
    private val files: BackupFileService,
    private val destination: BackupDestination,
    private val captureOrigin: CaptureOrigin,
    private val vault: BackupVaultRepository,
    private val switch: VaultSwitch,
    private val folder: VaultFolder,
    private val destinationChange: VaultDestinationChange,
    private val modalManager: ModalManager,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState(vault = vault.observe().value))
    val uiState = _uiState.asStateFlow()

    /**
     * The question a sheet is being asked, while it is being asked.
     *
     * The file is not here, and that is the point: it is a local of the flow that made it,
     * which is still running and is what removes it. This is only how the sheet's answer
     * reaches that flow — completed by one of the four answers, and gone as soon as one of
     * them has answered, so the second tap finds nothing left to answer.
     */
    private val pending = PendingAnswer()

    /**
     * The one part of the state the confirmation sheet reads. A modal is rendered outside
     * the screen's tree, by the manager that holds it, so it is handed the flow rather
     * than a value that was true when it was built.
     */
    val isRestoring: StateFlow<Boolean> = uiState
        .map { it.isRestoring }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The same arrangement, for the two sheets that outlive a value passed into them. */
    val vaultState: StateFlow<VaultState> = uiState
        .map { it.vault }
        .stateIn(viewModelScope, SharingStarted.Eagerly, vault.observe().value)

    val storedCopies: StateFlow<VaultCopies> = uiState
        .map { it.copiesInForce }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultCopies())

    /**
     * Whether a copy of the current archive is genuinely taken before it is replaced — the
     * one fact the confirmation may not get wrong.
     *
     * **It is the vault's answer for this action and not a reading of the switches.**
     * Whether a copy is owed is the two switches *and* the action's class, and
     * [com.neoutils.finsight.feature.backup.api.DestructiveClass] is the one owner of the
     * second half (design D7) — so a screen that asked only the switches would be a second
     * owner of the rule, agreeing with the trigger only for as long as restoring stays in a
     * covered class.
     *
     * A refusal takes it away for the rest of the flow. The copy was owed, it could not be
     * taken, and from that moment the sheet has nothing to promise.
     */
    val keepsCopy: StateFlow<Boolean> = uiState
        .map { it.vault.keepsCopyBefore(DestructiveAction.RESTORE_BACKUP) && !it.copyRefused }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            vault.observe().value.keepsCopyBefore(DestructiveAction.RESTORE_BACKUP),
        )

    init {
        vault.observe()
            .onEach { state -> _uiState.update { it.copy(vault = state) } }
            .launchIn(viewModelScope)

        folder.link
            .onEach { link -> _uiState.update { it.copy(folderLink = link) } }
            .launchIn(viewModelScope)

        // The link is checked when the app opens (`VaultAppOpening`), and again here: this
        // is the screen that says where the copies go, and a folder that stopped being
        // reachable since the app was opened would otherwise be reported by a line that is
        // hours old.
        viewModelScope.launch { folder.check() }

        readDestination()
    }

    /**
     * Changing where the copies go, which both screens offer and neither owns — see
     * [VaultDestinationFlow]. The reading afterwards is this screen's, and is the only part
     * of it that differs between the two.
     */
    private val destinationFlow = VaultDestinationFlow(
        change = destinationChange,
        modalManager = modalManager,
        scope = viewModelScope,
        refresh = ::readDestination,
        isBusy = { _uiState.value.isBusy },
    )

    fun onAction(action: BackupAction) {
        when (action) {
            is BackupAction.Export -> export(action.context)
            is BackupAction.ChooseFileToRestore -> chooseFileToRestore(action.context)
            BackupAction.Restore -> restore()
            BackupAction.DiscardCandidate -> discardCandidate()
            BackupAction.RestoreWithoutCopy -> answerRefusal(proceed = true)
            BackupAction.AbandonRestore -> answerRefusal(proceed = false)
            is BackupAction.SetVaultOn -> setVaultOn(action.isOn)
            is BackupAction.SetPeriodicOn -> vault.setPeriodicOn(action.isOn)
            is BackupAction.SetPreventiveOn -> vault.setPreventiveOn(action.isOn)
            is BackupAction.SetInterval -> vault.setInterval(action.interval.duration)
            is BackupAction.SetRetention -> vault.setRetention(action.retention)
            is BackupAction.ChooseFolder -> destinationFlow.chooseFolder(action.context)
            BackupAction.KeepInsideApp -> destinationFlow.keepInsideApp()
            BackupAction.Refresh -> readDestination()
        }
    }

    /**
     * Moves the switch, and lets turning the vault on mean what it means.
     *
     * The copy is [com.neoutils.finsight.domain.vault.VaultSwitch]'s, not this screen's:
     * the vault is turned on from here and from the offer beside a destructive
     * confirmation, and a first copy written here would be one the offer never takes. What
     * is this screen's is the saying — a vault that is on and holding nothing is the one
     * outcome the person watching this switch would otherwise never hear about, and the
     * kept-copies screen would go on promising a copy that already failed to arrive.
     *
     * The switch itself has already moved by the time this suspends: the preference is
     * written before the capture starts, so the toggle is not waiting on a `VACUUM INTO`.
     * The vault stays on either way. Undoing the person's choice because a file could not
     * be written would leave a deletion they are about to confirm meeting a vault that is
     * off — no copy, and no question about going on without one — and the destination they
     * chose may well be writable at the next trigger.
     */
    private fun setVaultOn(isOn: Boolean) {
        viewModelScope.launch {
            when (val outcome = switch.setOn(isOn)) {
                is CaptureOutcome.Failed -> fail(outcome.error)

                // What this screen says about the destination was read before a copy landed
                // in it, and the copy is this screen's own doing.
                is CaptureOutcome.Captured -> readDestination()

                CaptureOutcome.AlreadyCovered, CaptureOutcome.VaultOff -> Unit
            }
        }
    }


    /**
     * Reads what the destination holds, now.
     *
     * **Now includes coming back to this screen.** The copies screen sits on top of this
     * one while a copy is deleted there, and this route is still in the back stack when it
     * closes — so a read taken only at init leaves the card counting a file that is no
     * longer in the folder, over wording the two screens share precisely so they cannot
     * disagree about it (`BackupLabels`).
     *
     * A destination that cannot be read leaves the screen saying nothing about it rather
     * than saying zero: none of the three lines built from this — the count, the room they
     * take, the newest — is worth inventing, and the instant of the last successful capture
     * is a fact of this install that survives the folder being unreadable. So an unread
     * destination is left unread, and a re-read that fails leaves the last answer standing
     * rather than replacing it with zero.
     *
     * **The rung it was a reading of travels with it**, and it is read before the listing
     * rather than after, because that is the one the router used. Standing on is only right
     * while the destination is the same one: a reading kept across a change of rung is the
     * app's own storage counted under the name of a folder, which is the state a fallen link
     * and a change of destination both produce (see [BackupUiState.copiesInForce]).
     *
     * It is dispatched away from the main thread rather than merely suspending on it:
     * listing a folder is disk work, and the composition that started this screen has
     * nothing to wait for.
     */
    private fun readDestination() {
        viewModelScope.launch(Dispatchers.Default) {
            val rung = folder.rung.inForce
            val copies = destination.list().getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    copies = VaultCopies(
                        rung = rung,
                        count = copies.size,
                        totalBytes = copies.sumOf { copy -> copy.sizeInBytes },
                        newestAt = copies.firstOrNull()?.savedAt,
                    )
                )
            }
        }
    }

    private fun export(context: PlatformContext) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            try {
                files.newCapturePath().fold(
                    ifLeft = ::fail,
                    ifRight = { path -> captureInto(path, context) },
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                fail(BackupError.EXPORT_FAILED)
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    /**
     * Writes the archive to [path], offers the file, and removes it — the last one
     * whatever became of the other two.
     *
     * A user who closes the save dialog has not failed at anything and is told nothing:
     * choosing nowhere arrives as `false`, on the right side of the result, and only `true`
     * is worth a word — the file left for a place this screen cannot read back from.
     *
     * Only the capture's own refusals are named here, because they are the only ones with
     * a cause to read. Anything else the two calls raise leaves through [export], which
     * reports it as the export failing — after the `finally` below has taken the file away.
     */
    private suspend fun captureInto(path: String, context: PlatformContext) {
        try {
            database.captureInto(
                destinationPath = path,
                appVersion = captureOrigin.appVersion,
                platform = captureOrigin.platform.id,
            )
            files.copyOutCapturedFile(
                sourcePath = path,
                suggestedName = backupFileName(now()),
                context = context,
            ).fold(
                ifLeft = ::fail,
                ifRight = { saved -> if (saved) succeed(Res.string.backup_export_success) },
            )
        } catch (cause: DatabaseCaptureException) {
            fail(cause.error.toBackupError())
        } finally {
            withContext(NonCancellable) { files.discard(path) }
        }
    }

    /**
     * Picks a file and hands it to the restore, which owns everything from the gate to the
     * archive being replaced.
     *
     * A second run is refused while one is waiting on an answer, and that is what the
     * [answer] arm of the guard is for: the busy flags are down while the user reads the
     * sheet, and a second flow would ask a second question through the one field the first
     * one is listening on.
     */
    private fun chooseFileToRestore(context: PlatformContext) {
        if (_uiState.value.isBusy || pending.isWaiting) return
        _uiState.update { it.copy(isVerifying = true) }

        viewModelScope.launch {
            try {
                when (val outcome = archiveRestore.restoreFrom({ files.copyInChosenFile(context) }, questions)) {
                    RestoreOutcome.Restored -> succeed(Res.string.backup_restore_success)
                    RestoreOutcome.Abandoned -> Unit
                    is RestoreOutcome.Failed -> fail(outcome.error)
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        isRestoring = false,
                        confirmation = null,
                        captureRefusal = null,
                        copyRefused = false,
                    )
                }
                readDestination()
            }
        }
    }

    /**
     * The two questions this screen puts up, and where the answers come back from.
     *
     * The screen stops being busy as the confirmation goes up. What the user asked for —
     * pick a file and check it — is over, and holding the entries shut while somebody reads
     * a sheet would say the app is doing something when the only thing still running is one
     * coroutine, owning a file. It stays busy for the second question, because by then the
     * restore was asked for and has not been called off.
     */
    private val questions = object : RestoreQuestions {

        override suspend fun confirm(confirmation: RestoreConfirmation): Boolean =
            pending.ask { _uiState.update { it.copy(isVerifying = false, confirmation = confirmation) } }

        override suspend fun permitWithoutCopy(reason: UiText): Boolean =
            pending.ask { _uiState.update { it.copy(captureRefusal = reason, copyRefused = true) } }
    }


    /**
     * The user answered yes; the flow that owns the file goes on from where it is waiting.
     *
     * The answer is taken before anything else, so a second tap has nothing left to give
     * one with, and the entry is marked busy before the flow can resume: the operation is
     * not over for the screen one step before the user hears the result of it.
     */
    private fun restore() =
        pending.answer(proceed = true) { _uiState.update { it.copy(isRestoring = true) } }

    /**
     * The confirmation was dismissed without an answer, and the file goes with it — the
     * flow that was waiting on the answer is what removes it.
     *
     * A dismissal while the replacement is running is not one: it cannot be called off —
     * the transaction either lands or reverts — and the answer that started it has already
     * been taken, so there is nothing here left to give.
     */
    private fun discardCandidate() = pending.answer(proceed = false)

    /**
     * The user answered the question about restoring without a copy; the flow that owns the
     * file goes on from where it is waiting, either into the replacement or out of it.
     */
    private fun answerRefusal(proceed: Boolean) =
        pending.answer(proceed) { _uiState.update { it.copy(captureRefusal = null) } }

    private fun fail(error: BackupError) = modalManager.showError(error.toUiText())

    private fun succeed(message: StringResource) = modalManager.showSuccess(UiText.Res(message))

    private fun now(): LocalDateTime =
        clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
