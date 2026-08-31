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
 * two disagree about the one thing that matters, which is what *absence* means. The app's
 * own folder is absent because nothing has been captured yet, and answering an empty list
 * is the truth. A folder somebody chose is absent because a grant was revoked, a folder was
 * renamed, a volume was unmounted or a provider is still waking up — and answering an empty
 * list there is design D9's forbidden sentence: **zero copies means "could not read", never
 * "there is nothing here"**.
 *
 * **A cursor with no rows is refused here, and that is the sharp edge of the rule.** A
 * folder that provably existed and accepted a write moments later answered a complete-looking
 * cursor with no rows at all, once, right after a reboot, and it did not reproduce. So the
 * cost is paid where it is cheap: between pointing at a fresh folder and the first copy
 * landing in it, the history says it could not read the folder rather than that the folder
 * is empty. The alternative is the expensive mistake — "no copies yet" over an archive that
 * is sitting right there, and a retention sweep counting from zero.
 *
 * **Nothing here creates a directory.** Not on a write, not after a listing came back empty,
 * not ever: only [AndroidBackupFolder.point] makes the app's own subfolder, with a person in
 * front of the screen and with the provider's own reply as evidence rather than a listing.
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
        reachable().flatMap { own ->
            Either.catch {
                val resolver = appContext.contentResolver
                val document = DocumentsContract.createDocument(
                    resolver,
                    own.uri,
                    EXPORT_MIME_TYPE,
                    name,
                ) ?: throw IOException("The provider created no document for the copy")

                resolver.openOutputStream(document, "wt").use { sink ->
                    checkNotNull(sink) { "The provider opened no stream for the copy" }
                    File(capturedPath).inputStream().use { source -> source.copyTo(sink) }
                }

                val written = resolver.documentAt(document)
                    ?: throw IOException("The provider will not describe the copy it wrote")
                written.asStoredBackup()
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.IO) {
            reachable().flatMap { own ->
                Either.catch {
                    own.contents()
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
        reachable().flatMap { own ->
            Either.catch {
                val document = own.documentNamed(backup.name) ?: return@catch false
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
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val own = reachable().getOrElse { return it.left() }

        val found = withContext(Dispatchers.IO) {
            Either.catch { own.documentNamed(backup.name) }
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
                DocumentsContract.deleteDocument(appContext.contentResolver, document)
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The app's own subfolder inside the chosen one, as it stands — never as it could be
     * made to stand.
     *
     * Both refusals behind it are the same to a caller and different in kind: nothing was
     * ever pointed at, or what was pointed at cannot be reached now. What separates them
     * for a person is [com.neoutils.finsight.ui.screen.backup.service.FolderLink], which
     * the screen reads, and this stays the destination's own flat "I cannot".
     */
    private suspend fun reachable(): Either<BackupError, OwnFolder> =
        folder.ownFolder()?.right() ?: BackupError.EXPORT_FAILED.left()

    /**
     * Everything the folder holds, refusing an answer with no rows in it.
     *
     * @throws IOException when the listing cannot be trusted, an empty one included — see
     * the class comment for why emptiness is counted among the failures on this rung and
     * on no other.
     */
    private fun OwnFolder.contents() = appContext.contentResolver
        .childrenOf(tree, documentId)
        .ifEmpty { throw IOException("The folder answered no children at all") }

    /**
     * One copy by the name a listing gave it, or null when a listing that can be trusted
     * does not hold it.
     */
    private fun OwnFolder.documentNamed(name: String): Uri? = contents()
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
