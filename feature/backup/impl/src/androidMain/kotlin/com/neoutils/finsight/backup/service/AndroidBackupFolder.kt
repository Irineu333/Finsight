package com.neoutils.finsight.backup.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderIdentity
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.folderIdentity
import com.russhwolf.settings.Settings
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's half of design D4's machine: the system folder picker, and a tree `Uri` whose
 * permission is taken to be kept.
 *
 * **Only the tree `Uri` is written down** (task 11.1). The copies go straight into what was
 * chosen — there is no subfolder of the app's own inside it.
 *
 * **The `Uri` never leaves this module** (design D2). [BackupFolder] answers three words
 * about the link; the member that hands out a handle is `internal`, visible to
 * [AndroidFolderBackupDestination] beside it and to nothing in common code. Android would
 * tolerate a `Uri` making the round trip through text — that is how it is persisted here —
 * but the contract is shaped by iOS, where the same round trip destroys the permission, and
 * a destination that answered with a handle would be a destination that cannot be written
 * there.
 *
 * **The grant is taken and never released.** Re-taking the same tree is idempotent, so
 * pointing at a folder again costs nothing, and releasing the previous one on a switch was
 * refused: a release is irreversible without the person going back through the picker, and
 * the folder it would drop is the one holding an archive this app can no longer see.
 */
class AndroidBackupFolder(
    private val appContext: Context,
    private val settings: Settings,
    /**
     * How a folder is put to the person — the system picker by default, and the only part
     * of pointing at one that cannot be exercised anywhere. See [chooseTreeWithSaf].
     */
    private val choose: suspend (PlatformContext) -> Uri? = { chooseTreeWithSaf(it) },
) : BackupFolder {

    override val isOffered = true

    override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> =
        pointAt(choose(context))

    /**
     * Everything pointing at a folder means, once one has been pointed at.
     *
     * It is apart from [point] because the picker is the only half that needs a person in
     * front of the screen, and every rule is in this half: what a closed picker means and
     * when the preference is written.
     */
    internal suspend fun pointAt(tree: Uri?): Either<BackupError, Boolean> {
        if (tree == null) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                appContext.contentResolver
                    .takePersistableUriPermission(tree, PERSISTED_ACCESS)
                // Confirmed before the preference is written, and not after: a vault
                // pointed at a folder it cannot even list would be a vault that stops
                // writing at the next trigger.
                listChildren(tree)
                settings.putString(KEY_TREE, tree.toString())
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The link is the chosen folder answering a listing, not the tree `Uri` being
     * remembered.
     *
     * The stricter of the two readings is the right one: a grant that was revoked, a folder
     * the person deleted from a file manager and a volume that is not mounted all leave the
     * remembered `Uri` exactly as it was, and all three are a link that has fallen. None of
     * them is repaired here — what this does is read and report (design D12).
     */
    override suspend fun link(): FolderLink {
        if (storedTree() == null) return FolderLink.NONE
        return if (chosenFolder() != null) FolderLink.LINKED else FolderLink.BROKEN
    }

    /**
     * The tree `Uri`'s own text, fingerprinted — never the `Uri` itself, which stays
     * `internal` to this module (design D2). It is stable across everything that leaves
     * [KEY_TREE] untouched, which on this platform is everything but [pointAt] itself: the
     * persisted grant is re-taken rather than re-issued, so a folder chosen twice reads the
     * same tree both times.
     */
    override val identity: FolderIdentity?
        get() = settings.getStringOrNull(KEY_TREE)?.let(::folderIdentity)

    /**
     * What the provider calls the chosen folder, or null when nothing is pointed at or the
     * provider will no longer describe it.
     *
     * It costs one query over the tree's own root document — [ChosenFolder.uri] built from
     * the remembered tree, never from a listing — and not [chosenFolder]'s full reachability
     * check: a caller asking for a name is asking something readable, not proof the folder
     * can currently be written into.
     */
    override suspend fun displayName(): String? = withContext(Dispatchers.IO) {
        val tree = storedTree() ?: return@withContext null
        Either.catch {
            appContext.contentResolver.documentAt(ChosenFolder(tree).uri)?.name
        }.getOrNull()
    }

    /**
     * The chosen folder as the provider describes it now, or null when nothing has been
     * pointed at and when what was pointed at cannot be reached.
     *
     * The two are one answer on purpose, exactly as they are on the desktop: to a
     * destination they are the same refusal, and what separates them for a person is
     * [FolderLink], which the screen reads.
     */
    internal suspend fun chosenFolder(): ChosenFolder? = withContext(Dispatchers.IO) {
        val tree = storedTree() ?: return@withContext null
        val reachable = Either.catch { listChildren(tree) }.isRight()
        if (reachable) ChosenFolder(tree) else null
    }

    private fun storedTree(): Uri? = settings.getStringOrNull(KEY_TREE)?.let(Uri::parse)

    /**
     * Every child of the chosen folder's own root document, which is what proves it can
     * currently be read.
     *
     * @throws IOException when the tree could not be read at all — see [childrenOf].
     */
    private fun listChildren(tree: Uri) = appContext.contentResolver
        .childrenOf(tree, DocumentsContract.getTreeDocumentId(tree))

    private companion object {

        /**
         * Android's own key. Each platform remembers its own kind of token — a tree `Uri`
         * here, a path on the desktop, a bookmark on iOS — and no install ever reads
         * another platform's.
         */
        const val KEY_TREE = "backup_vault_tree_uri"

        /**
         * Both directions, because the vault does both: it writes copies into the folder
         * and reads them back out of it, and retention removes what it wrote.
         */
        const val PERSISTED_ACCESS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/**
 * The chosen folder, addressed as the two handles [DocumentsContract] needs: the tree the
 * grant is held over, and the document id of its own root — derivable from the tree without
 * a query, since it is the tree's own id.
 *
 * There is no subfolder of the app's own to search for any more: this *is* the folder
 * somebody chose, and [AndroidBackupFolder.chosenFolder] only ever hands one out once a
 * listing of it has actually succeeded.
 */
internal class ChosenFolder(val tree: Uri) {

    val documentId: String get() = DocumentsContract.getTreeDocumentId(tree)

    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
}

/**
 * Puts the system folder picker up and answers the tree that was pointed at, or null when
 * the person closed it.
 *
 * It is a function beside the class rather than a method on it because it is the one half
 * of pointing at a folder that needs a person in front of the screen, and the half that
 * cannot be exercised anywhere: no test on any platform drives a system picker. What a
 * chosen folder *means* is [AndroidBackupFolder.pointAt], and that is where the rules are.
 *
 * **The folder it opens on is a suggestion and nothing else** (task 11.3). The picker
 * refuses `Download` itself and the volume root, and `CREATE NEW FOLDER` is offered on that
 * same blocked screen, so somebody who lands there is one tap from a folder that works;
 * a subfolder of `Download` is as usable as any other, measured (Q2). Opening on
 * `Documents` spares the detour and decides nothing — wherever the person navigates to is
 * accepted, and no provider is ever asked who it is (design D16).
 */
internal suspend fun chooseTreeWithSaf(context: PlatformContext): Uri? =
    context.registry.awaitResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        input = DOCUMENTS_FOLDER,
    )

/**
 * Where the picker opens, on the volume every device has. It is passed as
 * `EXTRA_INITIAL_URI`, which a picker is free to ignore and which does nothing at all below
 * API 26.
 */
private val DOCUMENTS_FOLDER: Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Documents",
)
