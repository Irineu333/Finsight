package com.neoutils.finsight.backup.service

import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import arrow.core.Either
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The two document dialogs: the file the user points at, and the place the user puts one.
 *
 * Both are raised from a view model rather than a composition, over the registry the
 * activity already carries — see [awaitResult], which the folder picker uses too.
 */
class AndroidBackupFileService(private val appContext: Context) : BackupFileService {

    /**
     * The copy is removed unless the path is handed back, and it is a `finally` rather than
     * a failure path because the way it is most easily lost is not a failure: the copy runs
     * to the end whatever the caller's scope is doing, and [withContext] then raises the
     * cancellation instead of returning — the file exists and nobody has been told where.
     * A caller cannot close what it was never given, and the path is minted here.
     */
    override suspend fun copyInChosenFile(
        context: PlatformContext,
    ): Either<BackupError, String?> {
        var unclaimed: String? = null
        try {
            return Either.catch {
                val chosen = context.registry.awaitResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    input = arrayOf(EVERY_MIME_TYPE),
                ) ?: return@catch null

                withContext(Dispatchers.IO) {
                    val destination = createPrivateFile()
                    unclaimed = destination.absolutePath
                    context.activity.contentResolver.openInputStream(chosen).use { source ->
                        checkNotNull(source) { "The provider opened no stream for the chosen file" }
                        destination.outputStream().use(source::copyTo)
                    }
                    destination.absolutePath
                }
            }.onRight { unclaimed = null }
                .mapLeft { it.toBackupError(BackupError.VERIFICATION_FAILED) }
        } finally {
            unclaimed?.let { withContext(NonCancellable) { discard(it) } }
        }
    }

    override suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean> = Either.catch {
        val destination = context.registry.awaitResult(
            contract = ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
            input = suggestedName,
        ) ?: return@catch false

        withContext(Dispatchers.IO) {
            // "wt" truncates. The framework hands back an empty file of its own making
            // whenever it creates one, but it hands back the existing file when the user
            // answers a name clash by replacing, and a shorter backup written over a
            // longer one would otherwise keep the tail of the longer one.
            context.activity.contentResolver.openOutputStream(destination, "wt").use { sink ->
                checkNotNull(sink) { "The provider opened no stream for the chosen destination" }
                File(sourcePath).inputStream().use { source -> source.copyTo(sink) }
            }
        }
        true
    }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }

    override suspend fun newCapturePath(): Either<BackupError, String> =
        withContext(Dispatchers.IO) {
            Either.catch { createPrivateFile().apply { delete() }.absolutePath }
                .mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }

    override suspend fun discard(path: String) {
        withContext(Dispatchers.IO) {
            DATABASE_FILES.forEach { suffix -> File(path + suffix).delete() }
        }
    }

    /**
     * Somewhere the app may write and the system may reclaim, which is what both files of
     * a backup need: a candidate is opened with Room and migrated in place, then read once
     * and never wanted again, and a capture only lives until it has been handed over. The
     * cache directory is cleaned by the system, so a file nobody came back for is not kept
     * forever.
     */
    private fun createPrivateFile(): File {
        val directory = File(appContext.cacheDir, PRIVATE_DIRECTORY).apply { mkdirs() }
        return File.createTempFile(CANDIDATE_PREFIX, CANDIDATE_SUFFIX, directory)
    }
}

/**
 * A full disk is the one failure the user can act on, so it is the one told apart. The
 * message is what carries it: a write that runs out of room surfaces as an [IOException]
 * naming `ENOSPC`, either on its own or wrapped by whatever was copying at the time.
 */
internal fun Throwable.toBackupError(otherwise: BackupError): BackupError =
    if (isOutOfSpace()) BackupError.NO_SPACE else otherwise

private fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause }
    .take(MAX_CAUSE_DEPTH)
    .any { cause -> cause is IOException && OUT_OF_SPACE.any { it in cause.message.orEmpty() } }

/**
 * Everything, and deliberately: the framework guesses a file's type from its extension,
 * and Drive, Dropbox and the local provider guess differently for the same file. Anything
 * narrower greys out backups this app reads perfectly well. What a file is gets settled
 * afterwards, by reading it.
 */
private const val EVERY_MIME_TYPE = "*/*"

/**
 * A backup is bytes to everything but this app, and no registered type describes it any
 * better. Whether the framework would append an extension of its own on the strength of it
 * was the open question this carried — measured on a real export, it does not: the file
 * lands under the name it was offered, `finsight-backup-<date>.db`.
 *
 * It is also the type the folder rung creates its documents under, measured the same way
 * and mattering more there: nothing asks the person to confirm a name, and a provider that
 * decided `.db` did not suit the type would rename every copy out from under
 * [com.neoutils.finsight.ui.screen.backup.service.isBackupFileName] with nobody watching.
 */
internal const val EXPORT_MIME_TYPE = "application/octet-stream"

/**
 * Everything a backup file becomes once something opens it, and everything that has to go
 * when it does.
 *
 * A database is up to three files while it is open in write-ahead logging, and a candidate
 * is opened with Room. The fourth is the driver's: this app sets `BundledSQLiteDriver` on
 * every platform (`Database.kt`), and on Android that driver takes a `.lck` beside the file
 * it opens and leaves it there — measured, both next to the live archive and next to a kept
 * copy while the gate was reading it. Removing the main file alone would leave three behind
 * that nothing lists and nothing else will ever remove, in a folder the person opens with a
 * file manager.
 */
internal val DATABASE_FILES = listOf("", "-wal", "-shm", ".lck")

private const val PRIVATE_DIRECTORY = "backup"
private const val CANDIDATE_PREFIX = "candidate-"
private const val CANDIDATE_SUFFIX = ".db"
private const val MAX_CAUSE_DEPTH = 8
private val OUT_OF_SPACE = listOf("ENOSPC", "No space left on device")
