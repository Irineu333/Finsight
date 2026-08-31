@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import java.io.File
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The second rung on Android: the same four operations as [AndroidBackupDestination], over
 * the folder somebody pointed at instead of the one the uninstaller empties.
 *
 * **It is a class of its own and not the first rung over another directory**, because the
 * two disagree about what has to be established before *absence* may be said. The app's own
 * folder is there because the app makes it and nothing else can take it away, so an empty
 * listing of it is the truth on its own. A folder somebody chose can have had its grant
 * revoked, been renamed, been unmounted or be sitting behind a provider that is still waking
 * up, and every one of those is design D9's forbidden sentence waiting to be said: **zero
 * copies must never be read as "there is nothing here" until the app has established that the
 * folder is there**.
 *
 * **[reachable] establishes that before anything else does.** It cannot answer without the
 * chosen folder's own root document already having been listed
 * ([AndroidBackupFolder.chosenFolder]), so every operation below has already had a real
 * answer out of this provider, over this grant, on this volume, a moment earlier — and the
 * listing an operation itself performs follows immediately after, over the same node.
 *
 * **The copies in the folder are read and never moved, and only this app's are removed.**
 * The folder belongs to the person and may hold their files; [remove] proves a file is this
 * app's by reading it ([OwnCopyCheck]) before it takes it away.
 */
class AndroidFolderBackupDestination(
    private val appContext: Context,
    private val folder: AndroidBackupFolder,
    private val ownCopy: OwnCopyCheck,
    /**
     * Where a copy is written to be read. The gate that proves a file is this app's opens
     * it with Room, and Room opens a path — so a document in the folder is copied into the
     * app's own temporary area first, through the service that already owns that decision
     * and already knows every file a database opening leaves behind.
     */
    private val files: BackupFileService,
) : BackupDestination {

    /**
     * The name is asked for and not obeyed, and nothing here checks whether it is free.
     *
     * [DocumentsContract.createDocument] settles a clash itself — a provider given a name
     * already in use creates the document under another one — so a listing taken first to
     * find a free name would be a listing this rung cannot trust, spent on a question the
     * provider answers better. What lands is read back out of the provider and answered as
     * it stands, which is why [put] answers a [StoredBackup] rather than nothing.
     */
    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.IO) {
        reachable().flatMap { chosen ->
            Either.catch {
                val resolver = appContext.contentResolver
                val document = DocumentsContract.createDocument(
                    resolver,
                    chosen.uri,
                    EXPORT_MIME_TYPE,
                    name,
                ) ?: throw IOException("The provider created no document for the copy")

                try {
                    resolver.openOutputStream(document, "wt").use { sink ->
                        checkNotNull(sink) { "The provider opened no stream for the copy" }
                        File(capturedPath).inputStream().use { source -> source.copyTo(sink) }
                    }

                    resolver.documentAt(document)
                        ?.asStoredBackup()
                        ?: throw IOException("The provider will not describe the copy it wrote")
                } catch (cause: Exception) {
                    // The document already carries a name the app recognises, and what is in
                    // it is however far the copy got. Left there it would be listed as a
                    // copy, counted inside the window retention keeps, and refused by every
                    // removal from then on — a truncated database reads as corrupt, and
                    // corruption is not proof a file is this app's. Taking it back is the
                    // whole of what a provider allows here: there is no rename into place to
                    // lean on, so a process killed mid-write still leaves one.
                    DocumentsContract.deleteDocument(resolver, document)
                    throw cause
                }
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.IO) {
            reachable().flatMap { chosen ->
                Either.catch {
                    chosen.contents()
                        .filter { !it.isDirectory && isBackupFileName(it.name) }
                        .map { it.asStoredBackup() }
                        .sortedWith(NEWEST_FIRST)
                }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
            }
        }

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = withContext(Dispatchers.IO) {
        reachable().flatMap { chosen ->
            Either.catch {
                val document = chosen.documentNamed(backup.name) ?: return@catch false
                readOut(document, destinationPath)
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * Removes one copy, once the file has been confirmed by its content to be one this app
     * wrote — the promise that lets the vault sweep a folder full of somebody's own files.
     *
     * Proving it costs a copy: the gate opens a database with Room, Room opens a path, and
     * a document in somebody's folder is not one. So the copy comes into the app's own
     * temporary area, is read there, and leaves with everything a database opening put
     * beside it — none of which is ever written into the person's folder.
     *
     * A copy that a trustworthy listing does not hold is answered as removed: the folder is
     * one the person can also reach with a file manager, and there is nothing left to
     * refuse. A folder that could not be read at all is a different answer and refuses,
     * because nothing is known about the file either way.
     *
     * **False means one thing only: the content check refused.** A deletion that did not
     * happen is a failure and leaves as one — the screen turns false into a sentence about
     * what the file *is*, and saying that over a file the app never managed to unlink would
     * be a claim it has no evidence for.
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val chosen = reachable().getOrElse { return it.left() }

        val found = withContext(Dispatchers.IO) {
            Either.catch { chosen.documentNamed(backup.name) }
                .mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
        val document = found.getOrElse { return it.left() } ?: return true.right()

        val scratch = files.newCapturePath().getOrElse { return it.left() }
        try {
            val read = withContext(Dispatchers.IO) {
                Either.catch { readOut(document, scratch) }
                    .mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
            }
            read.getOrElse { return it.left() }

            if (!ownCopy.confirms(scratch)) return false.right()
        } finally {
            withContext(NonCancellable) { files.discard(scratch) }
        }

        return withContext(Dispatchers.IO) {
            Either.catch {
                val deleted =
                    DocumentsContract.deleteDocument(appContext.contentResolver, document)
                if (!deleted) throw IOException("The provider would not remove the copy")
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The chosen folder as it stands — never as it could be made to stand.
     *
     * Both refusals behind it are the same to a caller and different in kind: nothing was
     * ever pointed at, or what was pointed at cannot be reached now. What separates them
     * for a person is [com.neoutils.finsight.ui.screen.backup.service.FolderLink], which
     * the screen reads, and this stays the destination's own flat "I cannot".
     */
    private suspend fun reachable(): Either<BackupError, ChosenFolder> =
        folder.chosenFolder()?.right() ?: BackupError.EXPORT_FAILED.left()

    /**
     * Everything the folder holds, which may be nothing.
     *
     * Emptiness is an answer here only because of what had to happen for this to be reached:
     * the [ChosenFolder] it is asked of comes from [reachable] alone, and that means a
     * listing of this same node already succeeded a moment earlier. See the class comment.
     *
     * @throws IOException when the listing cannot be trusted — no cursor, or a cursor saying
     * through its own extras that it is still loading or has failed.
     */
    private fun ChosenFolder.contents() = appContext.contentResolver.childrenOf(tree, documentId)

    /**
     * One copy by the name a listing gave it, or null when a listing that can be trusted —
     * an empty one included — does not hold it.
     */
    private fun ChosenFolder.documentNamed(name: String): Uri? = contents()
        .firstOrNull { !it.isDirectory && it.name == name }
        ?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.documentId) }

    /** The copy in the folder is read and left exactly as it was. */
    private fun readOut(document: Uri, destinationPath: String) {
        appContext.contentResolver.openInputStream(document).use { source ->
            checkNotNull(source) { "The provider opened no stream for the copy" }
            File(destinationPath).outputStream().use { sink -> source.copyTo(sink) }
        }
    }
}

/**
 * What the provider says about a copy, and nothing read out of the copy itself — which is
 * the whole of what a listing is allowed to be (design D9).
 *
 * A provider that keeps no modification time answers zero, and the copies then order
 * themselves by name, which for this app's dated names is the same order. What it costs is
 * a date on the screen that is not the date the copy was taken, and never an order that
 * puts the newest copy where retention can reach it.
 */
private fun SafChild.asStoredBackup() = StoredBackup(
    name = name,
    savedAt = Instant.fromEpochMilliseconds(lastModified),
    sizeInBytes = sizeInBytes,
)
