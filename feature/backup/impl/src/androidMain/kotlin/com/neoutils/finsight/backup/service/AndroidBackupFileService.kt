package com.neoutils.finsight.backup.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import arrow.core.Either
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The storage access framework, reached without a lifecycle owner to reach it through.
 *
 * A file is asked for from a view model, not from a composition, so there is no
 * `rememberLauncherForActivityResult` to lean on and no owner whose destruction would tidy
 * a registration away. What there is instead is the registry the activity already carries
 * and an obligation to leave it as clean as it was found — [awaitResult] is where that
 * obligation is discharged.
 */
class AndroidBackupFileService(private val appContext: Context) : BackupFileService {

    override suspend fun copyInChosenFile(
        context: PlatformContext,
    ): Either<BackupError, String?> = Either.catch {
        val chosen = context.registry.awaitResult(
            contract = ActivityResultContracts.OpenDocument(),
            input = arrayOf(EVERY_MIME_TYPE),
        ) ?: return@catch null

        withContext(Dispatchers.IO) {
            val destination = createPrivateFile()
            context.activity.contentResolver.openInputStream(chosen).use { source ->
                checkNotNull(source) { "The provider opened no stream for the chosen file" }
                destination.outputStream().use(source::copyTo)
            }
            destination.absolutePath
        }
    }.mapLeft { it.toBackupError(BackupError.NOT_A_BACKUP) }

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
     * The registry the activity already owns.
     *
     * The cast holds because of how the context is built: `ProvidePlatformContext` takes
     * `LocalActivityResultRegistryOwner.current` and narrows it to `Activity`, so what is
     * in hand is an [ActivityResultRegistryOwner] that only its declared type hides.
     */
    private val PlatformContext.registry: ActivityResultRegistry
        get() = (activity as ActivityResultRegistryOwner).activityResultRegistry

    /**
     * Suspends until [contract] answers, and takes the registration back out on every way
     * this function can be left.
     *
     * The three-argument [ActivityResultRegistry.register] is the overload that takes no
     * lifecycle owner, and that is why it is the one used: the four-argument one throws
     * once the activity is past `STARTED`, which is the only moment a screen ever asks for
     * a file. The price of skipping it is that nothing unregisters on this code's behalf —
     * a callback left behind holds the continuation, and through it everything the
     * coroutine captured, for as long as the activity lives. So all four exits unregister:
     * the callback firing, the continuation being cancelled, `launch` throwing before there
     * is anything to wait for, and the replay `register` performs when the key it is given
     * already has a result waiting. Exactly one of them gets to do it — the launcher is
     * taken out of an [AtomicReference], so the cancellation handler, which runs on
     * whichever thread cancelled, cannot race the callback, which runs on the main one.
     *
     * The key is minted per call, so a result addressed to the key of a dead process is
     * never claimed by the next one. That is the intended outcome rather than a leak: the
     * process dying with the picker open takes the continuation with it, and until a file
     * arrives nothing has been changed that a missing result could leave half done.
     */
    private suspend fun <I, O> ActivityResultRegistry.awaitResult(
        contract: ActivityResultContract<I, O>,
        input: I,
    ): O = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val key = "$REGISTRY_KEY_PREFIX${UUID.randomUUID()}"
            val registered = AtomicReference<ActivityResultLauncher<I>?>(null)
            var answered = false

            val launcher = register(key, contract) { result ->
                answered = true
                registered.getAndSet(null)?.unregister()
                continuation.resume(result)
            }

            // Read before anything else can run on this thread, so it answers one question
            // only: did register() replay a pending result before handing the launcher
            // back? If it did, the callback had nothing to unregister with and this does.
            if (answered) {
                launcher.unregister()
                return@suspendCancellableCoroutine
            }

            registered.set(launcher)
            continuation.invokeOnCancellation {
                onMainThread { registered.getAndSet(null)?.unregister() }
            }

            try {
                launcher.launch(input)
            } catch (cause: Throwable) {
                // Nothing will call back, and this is not a cancellation, so neither of
                // the other two exits is going to run.
                registered.getAndSet(null)?.unregister()
                throw cause
            }
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
private fun Throwable.toBackupError(otherwise: BackupError): BackupError =
    if (isOutOfSpace()) BackupError.NO_SPACE else otherwise

private fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause }
    .take(MAX_CAUSE_DEPTH)
    .any { cause -> cause is IOException && OUT_OF_SPACE.any { it in cause.message.orEmpty() } }

private fun onMainThread(block: () -> Unit) {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() == mainLooper) block() else Handler(mainLooper).post(block)
}

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
 */
private const val EXPORT_MIME_TYPE = "application/octet-stream"

/**
 * A database is up to three files while it is open in write-ahead logging, and a
 * candidate is opened with Room. Removing the main file alone would leave the other two
 * behind until the system next reclaims the cache.
 */
private val DATABASE_FILES = listOf("", "-wal", "-shm")

private const val REGISTRY_KEY_PREFIX = "backup-file-service-"
private const val PRIVATE_DIRECTORY = "backup"
private const val CANDIDATE_PREFIX = "candidate-"
private const val CANDIDATE_SUFFIX = ".db"
private const val MAX_CAUSE_DEPTH = 8
private val OUT_OF_SPACE = listOf("ENOSPC", "No space left on device")
