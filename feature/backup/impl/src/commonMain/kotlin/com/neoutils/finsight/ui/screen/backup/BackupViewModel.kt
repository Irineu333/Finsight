@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.exception.DatabaseCaptureException
import com.neoutils.finsight.database.exception.DatabaseRestoreException
import com.neoutils.finsight.database.exception.DatabaseVerificationException
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
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_export_success
import com.neoutils.finsight.resources.backup_restore_success
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.backupFileName
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.CompletableDeferred
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
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.cancellation.CancellationException
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
 * **A flow lasts as long as the file it made.** The restore does not park its candidate in
 * a field and return: one coroutine copies the file in, asks about it, waits there for the
 * answer the sheet sends back, and replaces the archive — so the removal is one `finally`
 * over one body, and a screen that goes away while the sheet is up runs the same way out as
 * a screen whose user said no. A field would be a reference nothing outlives the view model
 * to read.
 *
 * **The confirmation is only ever asked about an approved file** (`local-backup` spec).
 * The gate runs first and in full, and its refusals reach the user as one sentence each;
 * asking before it has run would transfer a decision the app cannot yet stand behind.
 *
 * **A failure reaches the user rather than the crash handler.** Everything below runs in
 * [viewModelScope], where an exception that escapes is an uncaught crash and not a
 * message, so each operation catches what it can name, reports whatever else it meets as
 * the failure of the operation that met it, and rethrows [CancellationException] — the
 * one exception that has to keep travelling, and the one every removal here is already
 * written to survive. Removing a file is the exception that needs none: it is best effort
 * by contract, and raises nothing to report.
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
     * The question the confirmation sheet is being asked, while it is being asked.
     *
     * The file is not here, and that is the point: it is a local of the flow that made it,
     * which is still running and is what removes it. This is only how the sheet's answer
     * reaches that flow — completed by [restore] or [discardCandidate], and gone as soon as
     * one of them has answered, so the second tap finds nothing left to answer.
     */
    private var answer: CompletableDeferred<Boolean>? = null

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
                suggestedName = backupFileName(today()),
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
     * Copies in what the user picked, puts it through the gate, asks about it, and — if
     * the answer is yes — replaces the archive with it. One body, from the file arriving
     * to the file being gone.
     *
     * The copy is this flow's for the whole of its life, and every way out removes it:
     * refused, failed, replaced, dismissed, or walked away from. Nobody is coming back for
     * it, and the archive in use never knew it existed.
     *
     * A gate that could not run is not a gate that said no. The file is dropped either way
     * — nothing here can use it — but the word the user gets is about the check rather
     * than about what they picked, because a file that was never read has been found
     * nothing about.
     *
     * A second run is refused while one is waiting on an answer, and that is what the
     * [answer] arm of the guard is for: the busy flags are down while the user reads the
     * sheet, and a second flow would ask a second question through the one field the first
     * one is listening on.
     */
    private fun chooseFileToRestore(context: PlatformContext) {
        if (_uiState.value.isBusy || answer != null) return
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
                        if (!awaitAnswer(verification.toConfirmation())) return@launch

                        val error = replaceArchiveWith(chosen)
                        unclaimed = null
                        dropCandidate(chosen)
                        if (error != null) {
                            fail(error)
                        } else {
                            succeed(Res.string.backup_restore_success)
                        }
                    }

                    is CandidateVerification.Rejected -> {
                        fail(verification.reason.toBackupError())
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: DatabaseVerificationException) {
                fail(cause.error.toBackupError())
            } catch (cause: Exception) {
                fail(BackupError.VERIFICATION_FAILED)
            } finally {
                unclaimed?.let { dropCandidate(it) }
                _uiState.update { it.copy(isVerifying = false, isRestoring = false) }
            }
        }
    }

    /**
     * Puts the confirmation up and waits, here, for the answer the sheet sends back: true
     * to replace the archive, false to walk away from the file.
     *
     * The screen stops being busy as the waiting starts. What the user asked for — pick a
     * file and check it — is over, and holding the entries shut while somebody reads a
     * sheet would say the app is doing something when the only thing still running is this
     * coroutine, owning a file.
     */
    private suspend fun awaitAnswer(confirmation: RestoreConfirmation): Boolean {
        val pending = CompletableDeferred<Boolean>()
        answer = pending
        _uiState.update { it.copy(isVerifying = false, confirmation = confirmation) }

        return try {
            pending.await()
        } finally {
            answer = null
        }
    }

    /**
     * Replaces the archive with the approved file's content, in one transaction and
     * without closing anything — the screens go on rendering, and reflect the new archive
     * when it returns. The failure it could not carry out is the answer, rather than an
     * exception, because the file has to be removed and the user told either way.
     *
     * It runs under [NonCancellable] because there is nothing to call off. The swap either
     * lands or reverts, the sheet that asked for it refuses to be dismissed while it runs,
     * and the file is attached to the app's only writer connection until it returns — a
     * screen that went away mid-replacement would otherwise have the removal below race a
     * transaction still reading from the file.
     *
     * A failure leaves the archive exactly as it was, which is what the message says, and
     * the file that was going to replace it has no second chance to offer: it would have to
     * be picked and verified again. Either word is still said to a user who walked away
     * while it ran — the modal manager is the app's, not the screen's, and an archive that
     * has just become another one is not something to find out about by noticing.
     */
    private suspend fun replaceArchiveWith(path: String): BackupError? = try {
        withContext(NonCancellable) { database.replaceContentFrom(path) }
        null
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: DatabaseRestoreException) {
        cause.error.toBackupError()
    } catch (cause: Exception) {
        BackupError.RESTORE_FAILED
    }

    /**
     * The user answered yes; the flow that owns the file goes on from where it is waiting.
     *
     * The answer is taken before anything else happens, so a second tap has nothing left to
     * give one with, and the entry is marked busy before the flow can resume — the
     * operation is not over for the screen one step before the user hears the result of it.
     */
    private fun restore() {
        val pending = answer ?: return
        answer = null
        _uiState.update { it.copy(isRestoring = true) }
        pending.complete(true)
    }

    /**
     * The confirmation was dismissed without an answer, and the file goes with it — the
     * flow that was waiting on the answer is what removes it.
     *
     * A dismissal while the replacement is running is not one: it cannot be called off —
     * the transaction either lands or reverts — and the answer that started it has already
     * been taken, so there is nothing here left to give.
     */
    private fun discardCandidate() {
        val pending = answer ?: return
        answer = null
        pending.complete(false)
    }

    /**
     * The file goes first and the state second, so that the screen stops naming a
     * candidate only once there is no candidate left to name.
     */
    private suspend fun dropCandidate(path: String) {
        withContext(NonCancellable) { files.discard(path) }
        _uiState.update { it.copy(confirmation = null) }
    }

    private fun fail(error: BackupError) = modalManager.showError(error.toUiText())

    private fun succeed(message: StringResource) = modalManager.showSuccess(UiText.Res(message))

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
