@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseCaptureException
import com.neoutils.finsight.database.exception.DatabaseRestoreException
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.database.snapshot.replaceContentFrom
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.error.toBackupError
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.backupFileName
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The two flows of a local backup, end to end: capture then hand the file over, and
 * choose a file then verify, ask, and replace.
 *
 * **Both go through a file of this app's own that nothing else owns**, and removing it is
 * part of the flow rather than an afterthought. The capture writes to a temporary path
 * because it only knows how to write to one, and on two platforms the destination the
 * user picked is not a path; the candidate is a copy because the verification migrates
 * what it is handed. Neither has an owner other than this view model — `:core:database`
 * has no file API and is not getting one — so every way out of both flows removes them,
 * including the ways the user chose: a refused file, and a confirmation dismissed
 * without an answer. Leaving the screen is one of those ways and the one that takes the
 * most care: it cancels the scope both flows run in, and a suspending call in a `finally`
 * does not run once its coroutine is cancelled, so every removal here is made under
 * [NonCancellable].
 *
 * **The confirmation is only ever asked about an approved file** (`local-backup` spec).
 * The gate runs first and in full, and its refusals reach the user as one sentence each;
 * asking before it has run would transfer a decision the app cannot yet stand behind.
 */
class BackupViewModel(
    private val database: AppDatabase,
    private val candidateVerifier: CandidateVerifier,
    private val files: BackupFileService,
    private val captureOrigin: CaptureOrigin,
    private val modalManager: ModalManager,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * The file the confirmation is about, held here rather than in the state: it is a
     * path in this app's temporary area, the screen renders nothing from it, and this is
     * the only thing that will ever delete it.
     */
    private var candidatePath: String? = null

    /**
     * The one part of the state the confirmation sheet reads. A modal is rendered outside
     * the screen's tree, by the manager that holds it, so it is handed the flow rather
     * than a value that was true when it was built.
     */
    val isRestoring: StateFlow<Boolean> = uiState
        .map { it.isRestoring }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onAction(action: BackupAction) {
        when (action) {
            is BackupAction.Export -> export(action.context)
            is BackupAction.ChooseFileToRestore -> chooseFileToRestore(action.context)
            BackupAction.Restore -> restore()
            BackupAction.DiscardCandidate -> discardCandidate()
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
     * choosing nowhere arrives as `false`, on the right side of the result.
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
                suggestedName = backupFileName(today()),
                context = context,
            ).onLeft(::fail)
        } catch (cause: DatabaseCaptureException) {
            fail(cause.error.toBackupError())
        } finally {
            withContext(NonCancellable) { files.discard(path) }
        }
    }

    /**
     * Copies in what the user picked, puts it through the gate, and only then has
     * anything to ask about.
     *
     * The copy is this flow's until the confirmation claims it, and every other way out
     * removes it — refused, failed, or walked away from. Nobody is coming back for it,
     * and the archive in use never knew it existed.
     */
    private fun chooseFileToRestore(context: PlatformContext) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isVerifying = true) }

        viewModelScope.launch {
            var unclaimed: String? = null
            try {
                val chosen = files.copyInChosenFile(context).fold(
                    ifLeft = { error -> fail(error); null },
                    ifRight = { it },
                ) ?: return@launch

                unclaimed = chosen

                when (val verification = candidateVerifier.verify(chosen)) {
                    is CandidateVerification.Accepted -> {
                        candidatePath = chosen
                        unclaimed = null
                        _uiState.update { it.copy(confirmation = verification.toConfirmation()) }
                    }

                    is CandidateVerification.Rejected -> {
                        fail(verification.reason.toBackupError())
                    }
                }
            } finally {
                unclaimed?.let { withContext(NonCancellable) { files.discard(it) } }
                _uiState.update { it.copy(isVerifying = false) }
            }
        }
    }

    /**
     * Replaces the archive with the approved file's content, in one transaction and
     * without closing anything — the screens go on rendering, and reflect the new archive
     * when it returns.
     *
     * The candidate is dropped afterwards either way. A failure leaves the archive exactly
     * as it was, which is what the message says, and the file that was going to replace it
     * has no second chance to offer: it would have to be picked and verified again.
     */
    private fun restore() {
        val path = candidatePath ?: return
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(isRestoring = true) }

        viewModelScope.launch {
            val error = try {
                database.replaceContentFrom(path)
                null
            } catch (cause: DatabaseRestoreException) {
                cause.error.toBackupError()
            } finally {
                dropCandidate()
                _uiState.update { it.copy(isRestoring = false) }
            }

            error?.let(::fail)
        }
    }

    /**
     * The confirmation was dismissed without an answer, and the file goes with it.
     *
     * A dismissal while the replacement is running is not one: it cannot be called off —
     * the transaction either lands or reverts — and the file is still being read from, so
     * the operation itself is what removes it when it is done.
     */
    private fun discardCandidate() {
        if (_uiState.value.isRestoring) return
        viewModelScope.launch { dropCandidate() }
    }

    /**
     * The file goes first and the state second, so that the screen stops naming a
     * candidate only once there is no candidate left to name.
     */
    private suspend fun dropCandidate() {
        val path = candidatePath ?: return
        candidatePath = null
        withContext(NonCancellable) { files.discard(path) }
        _uiState.update { it.copy(confirmation = null) }
    }

    private fun fail(error: BackupError) = modalManager.showError(error.toUiText())

    private fun today(): LocalDate =
        clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

/**
 * The verification's word about a file, as the confirmation states it.
 *
 * The counts arrive typed by facade and are passed on as they came: which tables they were
 * counted from is `:core:database`'s business, and an entity added to the schema later is
 * not something this screen has to remember.
 */
private fun CandidateVerification.Accepted.toConfirmation() = RestoreConfirmation(
    origin = origin?.let {
        FileOrigin(
            platform = BackupPlatform.ofId(it.platform),
            platformId = it.platform,
            appVersion = it.appVersion,
            createdAt = Instant.fromEpochMilliseconds(it.createdAt),
        )
    },
    counts = counts,
)
