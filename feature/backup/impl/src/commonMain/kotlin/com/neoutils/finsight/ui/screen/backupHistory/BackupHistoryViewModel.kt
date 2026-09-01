package com.neoutils.finsight.ui.screen.backupHistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.restore.ArchiveRestore
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.restore.RestoreOutcome
import com.neoutils.finsight.domain.restore.RestoreQuestions
import com.neoutils.finsight.domain.vault.ArchiveImport
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.CarryOffer
import com.neoutils.finsight.domain.vault.ImportOutcome
import com.neoutils.finsight.domain.vault.KeptCopyFacts
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.MigrationOutcome
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_carry_done
import com.neoutils.finsight.resources.backup_carry_partial
import com.neoutils.finsight.resources.backup_export_success
import com.neoutils.finsight.resources.backup_history_capture_done
import com.neoutils.finsight.resources.backup_history_empty_off
import com.neoutils.finsight.resources.backup_history_gone
import com.neoutils.finsight.resources.backup_history_import_done
import com.neoutils.finsight.resources.backup_history_remove_refused
import com.neoutils.finsight.resources.backup_history_removed
import com.neoutils.finsight.resources.backup_restore_success
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.carryCopies.CarryCopiesModal
import com.neoutils.finsight.util.UiText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/**
 * The copies the vault keeps: where they are kept, what a person does with one of them, and
 * the two ways another one arrives.
 *
 * **Where they are kept is chosen here now, and the choice is not implemented here.** It is
 * [VaultDestinationChange]'s, for the reason the restore is [ArchiveRestore]'s: the backup
 * screen offers the same change from the card that announces a fallen link (design D12), and
 * two screens offering it must not be two readings of where the copies were going before the
 * move.
 *
 * **Bringing a file in is not restoring one.** [ArchiveImport] checks it at the restore's own
 * gate and puts it where the copies live; the archive is untouched, and nothing about which
 * copy the running app came from moves — an imported file is not one.
 *
 * **The listing is a reading of the destination, never a record.** It is taken when the
 * screen opens and again after anything that could have changed it, so a copy removed with
 * a file manager stops being listed without an error being made of it (design D9). Nothing
 * about a copy is kept anywhere: what a file *is* gets decided by reading the file, which
 * is what the restore and the removal both do.
 *
 * **Restoring is not implemented here.** It is [ArchiveRestore]'s, exactly as it is for the
 * backup screen: the two differ only in how the candidate arrives — one from a picker, one
 * out of the destination — and two screens offering a restore must not be two decisions
 * about when the person is asked or whether a copy that could not be taken may be walked
 * past. What is here is the asking, because the sheets are this screen's.
 *
 * **Nothing leaves the destination by path.** A copy is written into a temporary file of
 * this app's own and that file is what is restored from or handed to a picker — the
 * destination never answers with a path, because on iOS a folder's permission dies on the
 * way through a string (design D2). The temporary is removed on every way out, under
 * [NonCancellable], because a suspending call in a `finally` does not run once its
 * coroutine is cancelled.
 *
 * **Handing a copy out captures nothing.** It is the same picker the manual export uses,
 * over the file that already exists — which is the way off the device on a platform where
 * the vault is local and no cloud provider appears in a folder picker.
 *
 * **One tap opens one file, and the listing opens none.** What a copy holds is inside it,
 * so the sheet that describes a tapped copy is the only thing here that reads one — and it
 * reads the copy that was tapped, while the sheet is already up. Reading the folder stays
 * exactly what it was: a listing, of what the file system says (design D9).
 */
class BackupHistoryViewModel(
    private val destination: BackupDestination,
    private val files: BackupFileService,
    private val archiveRestore: ArchiveRestore,
    private val reader: KeptCopyReader,
    private val state: BackupVaultRepository,
    /**
     * Which rung the listing is of, from the one place that pairs the choice with the
     * reading of the link ([VaultFolder.rung]). The screen names the destination it lists,
     * and while a chosen folder cannot be reached the copies it lists are the ones inside
     * the app — so reading the preference alone would put the folder's name over the app's
     * own files.
     */
    private val folder: VaultFolder,
    private val vault: BackupVault,
    /** Bringing a file in, which is the one thing here that starts outside the destination. */
    private val archiveImport: ArchiveImport,
    /**
     * Moving where the copies are kept, and offering the ones left behind. It is the same
     * object the backup screen's recovery buttons use, because the reading taken before a
     * move is the one thing two screens offering the change may not each hold.
     */
    private val destinationChange: VaultDestinationChange,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BackupHistoryUiState(
            destination = folder.rung.inForce,
            isVaultOn = state.observe().value.isOn,
            archiveCopy = state.observe().value.archiveCopy,
        )
    )
    val uiState = _uiState.asStateFlow()

    /**
     * The answer a sheet sends back, while it is being asked for. The file it is about is a
     * local of the flow that made it, which is still running and is what removes it.
     */
    private var answer: CompletableDeferred<Boolean>? = null

    /**
     * The one part of the state the confirmation sheet reads. A modal is rendered outside
     * the screen's tree, by the manager that holds it, so it is handed the flow rather than
     * a value that was true when it was built.
     */
    val isRestoring: StateFlow<Boolean> = uiState
        .map { it.isRestoring }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Whether a copy of the current archive is genuinely taken before it is replaced.
     *
     * It is read from the vault rather than assumed from the screen being reachable: a
     * person can be looking at kept copies with the preventive trigger switched off, and
     * the confirmation is not allowed to promise a way back that nothing will write
     * (`local-backup` spec).
     *
     * **The action is named, and the vault answers for it.** Whether a copy is owed is the
     * switches *and* the action's class, and
     * [com.neoutils.finsight.feature.backup.api.DestructiveClass] owns the second half
     * (design D7); a screen that read only the switches would agree with the trigger by
     * coincidence. A refusal then takes the promise away for the rest of the flow, because
     * from that moment there is nothing to promise.
     */
    val keepsCopy: StateFlow<Boolean> = combine(state.observe(), uiState) { vaultState, screen ->
        vaultState.keepsCopyBefore(DestructiveAction.RESTORE_BACKUP) && !screen.copyRefused
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        state.observe().value.keepsCopyBefore(DestructiveAction.RESTORE_BACKUP),
    )

    /**
     * What the sheet about one copy reads. It is the flow and not a value for the reason
     * [isRestoring] is: the sheet is built at the tap and rendered by the manager that holds
     * it, so a value passed in would still say *reading* once the file had answered.
     *
     * **It is not part of [uiState], and that is what keeps the list still.** A reading of
     * one file changes state twice — once when it starts, once when it answers — and both
     * land inside the entrance of the sheet that asked for it. Carried in the screen's own
     * state, each of them recomposed the whole history behind the sheet on exactly the
     * frames the sheet is animating over it. Nothing in the list reads this; only the sheet
     * does, so only the sheet is subscribed to it.
     */
    private val _facts = MutableStateFlow<KeptCopyFacts>(KeptCopyFacts.Reading)
    val facts = _facts.asStateFlow()

    /**
     * The reading a sheet is waiting on. It is held so the next tap can call it off: a copy
     * nobody is looking at any more is a file being opened for nothing, and its answer must
     * never land in a sheet about a different one.
     */
    private var inspection: Job? = null

    init {
        _uiState.update { it.copy(isFolderOffered = folder.isOffered) }
        refresh()
    }

    fun onAction(action: BackupHistoryAction) {
        when (action) {
            BackupHistoryAction.Refresh -> refresh()
            BackupHistoryAction.Capture -> capture()
            is BackupHistoryAction.Import -> importFile(action.context)
            is BackupHistoryAction.ChooseFolder -> chooseFolder(action.context)
            BackupHistoryAction.KeepInsideApp -> keepInsideApp()
            is BackupHistoryAction.Inspect -> inspect(action.backup)
            is BackupHistoryAction.Restore -> restore(action.backup)
            is BackupHistoryAction.Share -> share(action.backup, action.context)
            is BackupHistoryAction.Remove -> askToRemove(action.backup)
            BackupHistoryAction.ConfirmRemove -> confirmRemoval()
            BackupHistoryAction.AbandonRemoval -> abandonRemoval()
            BackupHistoryAction.ConfirmRestore -> confirmRestore()
            BackupHistoryAction.DiscardCandidate -> answerConfirmation(proceed = false)
            BackupHistoryAction.RestoreWithoutCopy -> answerRefusal(proceed = true)
            BackupHistoryAction.AbandonRestore -> answerRefusal(proceed = false)
        }
    }

    /**
     * Reads the destination, off the main thread: listing a folder is disk work, and the
     * composition that opened this screen has nothing to wait for.
     */
    private fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val vaultState = state.observe().value
            val rung = folder.rung.inForce
            // Asked beside the listing and not instead of it: a name is not proof the
            // folder can be read, and [folder.displayPath] answers null on its own where
            // that proof is missing (design D9's forbidden sentence is about the listing
            // below, not about what the destination is called).
            val name = folder.displayPath()
            destination.list().fold(
                ifLeft = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isUnreadable = true,
                            destination = rung,
                            folderPath = name,
                            isVaultOn = vaultState.isOn,
                            archiveCopy = vaultState.archiveCopy,
                        )
                    }
                },
                ifRight = { copies ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isUnreadable = false,
                            destination = rung,
                            folderPath = name,
                            isVaultOn = vaultState.isOn,
                            archiveCopy = vaultState.archiveCopy,
                            copies = copies,
                            totalBytes = copies.sumOf { copy -> copy.sizeInBytes },
                        )
                    }
                },
            )
        }
    }

    /**
     * Takes a copy because the person asked for one, and reads the destination again so the
     * new file is in the list and carries the mark.
     *
     * **Nothing about design D8 is decided here.** A copy asked for is written whether or
     * not the one already there still covers the archive, and that difference is
     * [BackupVault.captureNow]'s — a screen that skipped on its own would be a second owner
     * of a rule the vault holds, and would have to explain the skip in a sentence that is
     * not reliably true.
     *
     * **A refusal is said, and never dressed as a copy.** Only a capture that landed shows
     * the success; everything else reaches [fail] or the sentence about the vault being off.
     *
     * The second press is what [BackupHistoryUiState.isBusy] is for: it goes up before the
     * capture starts and comes down after the listing, so the button, the rows and this
     * guard are the same fact.
     */
    private fun capture() {
        if (_uiState.value.isBusy) return
        inspection?.cancel()
        _uiState.update { it.copy(isCapturing = true) }

        viewModelScope.launch {
            try {
                // Off the caller's thread: a capture writes the whole archive out, and what
                // asked for it is a composition.
                when (val outcome = withContext(Dispatchers.Default) { vault.captureNow() }) {
                    is CaptureOutcome.Captured -> succeed(Res.string.backup_history_capture_done)
                    is CaptureOutcome.Failed -> fail(outcome.error)

                    // The vault is off, so nothing was written (design D1). The control is
                    // not offered then, but the refusal is the vault's rather than the
                    // screen's, and a refusal that says nothing is a button that looks
                    // broken. `captureNow` never answers `AlreadyCovered`; the branch is
                    // here because the type has four.
                    CaptureOutcome.VaultOff,
                    CaptureOutcome.AlreadyCovered ->
                        modalManager.showError(UiText.Res(Res.string.backup_history_empty_off))
                }
            } finally {
                _uiState.update { it.copy(isCapturing = false) }
                refresh()
            }
        }
    }

    /**
     * Brings a file the person picks into the destination, and reads the destination again
     * so the copy that landed is in the list.
     *
     * **Nothing about what a valid file is gets decided here.** The gate is
     * [ArchiveImport]'s and it is the restore's own, so a file this screen accepts is a file
     * the restore would accept — and a file it refuses is refused in the words that refusal
     * already has, because somebody who picked a file the app will not keep is owed the
     * reason.
     *
     * **It does not restore, and it does not mark.** The archive is untouched, and the copy
     * that lands takes no mark of any kind: the mark says which copy the running app came
     * from, and an imported file is not that (see [BackupHistoryUiState.archiveCopy]).
     */
    private fun importFile(context: PlatformContext) {
        if (_uiState.value.isBusy) return
        inspection?.cancel()
        _uiState.update { it.copy(isImporting = true) }

        viewModelScope.launch {
            try {
                when (val outcome = archiveImport.importChosenFile(context)) {
                    is ImportOutcome.Imported -> succeed(Res.string.backup_history_import_done)
                    is ImportOutcome.Failed -> fail(outcome.error)

                    // A picker somebody closed is not a failure and has nothing to say.
                    ImportOutcome.Abandoned -> Unit

                    // The vault is off, so nothing may be written into its destination
                    // (design D1). The control is not offered then, but the refusal is the
                    // vault's rather than the screen's, and one that said nothing would be
                    // a button that looks broken.
                    ImportOutcome.VaultOff ->
                        modalManager.showError(UiText.Res(Res.string.backup_history_empty_off))
                }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
                refresh()
            }
        }
    }

    /**
     * Points at a folder to keep the copies in, and offers to carry the ones left behind.
     *
     * The move and the offer are [VaultDestinationChange]'s. A picker somebody closed
     * changes nothing and says nothing; only a folder that could not be prepared reaches the
     * person, because only that leaves them with something to do.
     */
    private fun chooseFolder(context: PlatformContext) {
        viewModelScope.launch {
            destinationChange.pointAtFolder(context).fold(
                ifLeft = ::fail,
                ifRight = { offer ->
                    refresh()
                    offer?.let(::offerToCarry)
                },
            )
        }
    }

    /**
     * Moves the copies back inside the app.
     *
     * Nothing is removed and nothing is forgotten: the copies in the folder stay in it, and
     * the folder stays remembered, so pointing at it again leads back to them (design D4).
     */
    private fun keepInsideApp() {
        viewModelScope.launch {
            val offer = destinationChange.keepInsideApp()
            refresh()
            offer?.let(::offerToCarry)
        }
    }

    /**
     * Asks whether the copies left behind should be written into the destination now in
     * force — a question, never a step taken on the way past (design D13).
     */
    private fun offerToCarry(offer: CarryOffer) {
        modalManager.show(
            CarryCopiesModal(
                copies = offer.copies,
                onCarry = { carry(offer) },
                onDeclined = destinationChange::declineCarry,
            )
        )
    }

    /**
     * Carries the copies across, and says how far it got. A run that stopped partway is a
     * sentence of its own: some of them are still only in the place they were.
     */
    private fun carry(offer: CarryOffer) {
        viewModelScope.launch {
            when (destinationChange.carry(offer)) {
                is MigrationOutcome.Carried -> succeed(Res.string.backup_carry_done)

                is MigrationOutcome.Interrupted ->
                    modalManager.showError(UiText.Res(Res.string.backup_carry_partial))

                MigrationOutcome.NothingToCarry -> Unit
            }

            refresh()
        }
    }

    /**
     * Reads [backup] — the one copy whose sheet has just been opened — off the main thread.
     *
     * Nothing waits for it. The sheet is already up with what the listing knew, and the
     * facts land in it when the file answers; a copy that cannot be opened says so, which is
     * the honest end of a folder the user can also reach with a file manager (design D9).
     *
     * The previous reading is called off first and the state goes back to
     * [KeptCopyFacts.Reading], so a slow answer about the copy tapped before this one cannot
     * arrive in the sheet about this one.
     */
    private fun inspect(backup: StoredBackup) {
        inspection?.cancel()
        _facts.value = KeptCopyFacts.Reading

        inspection = viewModelScope.launch(Dispatchers.Default) {
            _facts.value = reader.read(backup)
        }
    }

    /**
     * Replaces the archive with [backup]'s content, having copied it out of the destination
     * first.
     *
     * The copy taken out is this flow's for the whole of its life and the restore removes
     * it; the file in the destination is read and left exactly as it was, so a restore
     * that goes wrong costs nothing that was being kept.
     */
    private fun restore(backup: StoredBackup) {
        if (_uiState.value.isBusy || answer != null) return
        inspection?.cancel()
        _uiState.update { it.copy(working = backup) }

        viewModelScope.launch {
            try {
                val outcome = archiveRestore.restoreFrom(
                    candidate = { copyOut(backup) },
                    questions = questions,
                    // Which copy the archive will have come from, so the list can say
                    // where the person is standing once this returns.
                    from = backup,
                )
                when (outcome) {
                    RestoreOutcome.Restored -> succeed(Res.string.backup_restore_success)
                    RestoreOutcome.Abandoned -> Unit
                    is RestoreOutcome.Failed -> fail(outcome.error)
                }
            } finally {
                _uiState.update {
                    it.copy(
                        working = null,
                        isRestoring = false,
                        confirmation = null,
                        captureRefusal = null,
                        copyRefused = false,
                    )
                }
                refresh()
            }
        }
    }

    /**
     * Hands [backup] to wherever the user chooses, as it is.
     *
     * No capture happens: the file being offered is the one the vault already wrote, which
     * is the whole point — it is how a copy gets off a device where the vault is local.
     */
    private fun share(backup: StoredBackup, context: PlatformContext) {
        if (_uiState.value.isBusy) return
        inspection?.cancel()
        _uiState.update { it.copy(working = backup) }

        viewModelScope.launch {
            var taken: String? = null
            try {
                val path = copyOut(backup).fold(
                    ifLeft = { error -> fail(error); null },
                    ifRight = { it },
                ) ?: return@launch

                taken = path

                files.copyOutCapturedFile(
                    sourcePath = path,
                    suggestedName = backup.name,
                    context = context,
                ).fold(
                    ifLeft = ::fail,
                    ifRight = { saved -> if (saved) succeed(Res.string.backup_export_success) },
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                fail(BackupError.EXPORT_FAILED)
            } finally {
                taken?.let { withContext(NonCancellable) { files.discard(it) } }
                _uiState.update { it.copy(working = null) }
            }
        }
    }

    /**
     * Puts the removal to the person, and removes nothing.
     *
     * The copy is the only thing on this screen that cannot be made again — the history is
     * a reading of the folder rather than a record (design D9), so a copy that leaves has
     * left and nothing here can put it back. One tap on a list is where that costs the
     * most, and every other destructive act in this app is confirmed first.
     *
     * The reading opened by the sheet that was just closed is called off here rather than
     * at the answer: nobody is looking at it any more the moment this question goes up.
     */
    private fun askToRemove(backup: StoredBackup) {
        if (_uiState.value.isBusy) return
        inspection?.cancel()
        _uiState.update { it.copy(pendingRemoval = backup) }
    }

    /** The question was answered by leaving the copy where it is. */
    private fun abandonRemoval() {
        _uiState.update { it.copy(pendingRemoval = null) }
    }

    /**
     * The answer, over the copy the question was asked about — never over whatever the row
     * under the sheet has become since.
     */
    private fun confirmRemoval() {
        val backup = _uiState.value.pendingRemoval ?: return
        _uiState.update { it.copy(pendingRemoval = null) }
        remove(backup)
    }

    /**
     * Removes [backup], if the destination confirms by reading it that this app wrote it.
     *
     * A refusal is said out loud rather than swallowed: the folder may be the user's own,
     * the app removes only its own files there, and somebody who asked for a removal that
     * did not happen is owed the reason. **It is said as a refusal**, in the shape this app
     * gives one, and never under the tick that means an operation did what was asked: a
     * green check above "it was not removed" leaves the person to decide which half to
     * believe.
     *
     * **A removal that happened is reported to the vault, and the vault decides what it
     * meant.** Whether the file that has just gone was the one holding the archive covered
     * is design D8's question and not a screen's, so what is said here is the fact — this
     * copy is no longer in the destination — and [BackupVault.copyRemoved] is what draws
     * anything from it. Saying nothing would leave the vault covered by a file the user
     * watched this screen delete.
     */
    private fun remove(backup: StoredBackup) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(working = backup) }

        viewModelScope.launch {
            try {
                destination.remove(backup).fold(
                    ifLeft = ::fail,
                    ifRight = { removed ->
                        if (removed) {
                            vault.copyRemoved(backup)
                            succeed(Res.string.backup_history_removed)
                        } else {
                            modalManager.showError(
                                UiText.Res(Res.string.backup_history_remove_refused)
                            )
                        }
                    },
                )
            } finally {
                _uiState.update { it.copy(working = null) }
                refresh()
            }
        }
    }

    /**
     * A private copy of [backup], at a path this app owns and throws away.
     *
     * Null where the copy is no longer in the destination — somebody removed it between the
     * listing and the tap — and the person is told, because they asked for something about
     * a file that is not there any more.
     */
    private suspend fun copyOut(backup: StoredBackup): Either<BackupError, String?> =
        files.newCapturePath().flatMap { path ->
            destination.copyOut(backup, path).flatMap { copied ->
                if (copied) {
                    path.right()
                } else {
                    withContext(NonCancellable) { files.discard(path) }
                    modalManager.showError(UiText.Res(Res.string.backup_history_gone))
                    null.right()
                }
            }
        }

    /** The two questions this screen puts up, and where the answers come back from. */
    private val questions = object : RestoreQuestions {

        override suspend fun confirm(confirmation: RestoreConfirmation): Boolean =
            await { it.copy(confirmation = confirmation) }

        override suspend fun permitWithoutCopy(reason: UiText): Boolean =
            await { it.copy(captureRefusal = reason, copyRefused = true) }
    }

    private suspend fun await(ask: (BackupHistoryUiState) -> BackupHistoryUiState): Boolean {
        val pending = CompletableDeferred<Boolean>()
        answer = pending
        _uiState.update(ask)

        return try {
            pending.await()
        } finally {
            answer = null
        }
    }

    private fun confirmRestore() {
        val pending = answer ?: return
        answer = null
        _uiState.update { it.copy(isRestoring = true) }
        pending.complete(true)
    }

    private fun answerConfirmation(proceed: Boolean) {
        val pending = answer ?: return
        answer = null
        pending.complete(proceed)
    }

    private fun answerRefusal(proceed: Boolean) {
        val pending = answer ?: return
        answer = null
        _uiState.update { it.copy(captureRefusal = null) }
        pending.complete(proceed)
    }

    private fun fail(error: BackupError) = modalManager.showError(error.toUiText())

    private fun succeed(message: StringResource) = modalManager.showSuccess(UiText.Res(message))
}
