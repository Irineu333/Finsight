package com.neoutils.finsight.backup.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.result.contract.ActivityResultContracts
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.russhwolf.settings.Settings
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's half of design D4's machine: the system folder picker, a tree `Uri` whose
 * permission is taken to be kept, and the app's own subfolder made inside whatever was
 * pointed at.
 *
 * **Only the tree `Uri` is written down** (task 11.1). The app's own subfolder is found
 * again by name on each use rather than remembered by document id, because the id is the
 * provider's to reissue and the grant is over the tree — a remembered child would be a
 * second thing that can go stale, and the one thing it would save is a query.
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
 *
 * **Only this creates the app's own subfolder, and only with a person in front of the
 * screen.** Nothing on the writing path ever makes it (design D9). What makes that safe
 * here is not the listing — a folder that provably existed once answered a complete-looking
 * cursor with no rows at all — but the provider's own reply to [DocumentsContract.createDocument]:
 * a provider handed a name that is already taken creates the folder under a different one,
 * and that different name is proof that the listing was wrong. The duplicate is removed and
 * the real folder is looked for again, so nobody ends up with an archive split in two at the
 * moment they are trying to recover it (design D4).
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
     * front of the screen, and every rule is in this half: what a closed picker means, what
     * is kept, which folder is made, and when the preference is written.
     */
    internal suspend fun pointAt(tree: Uri?): Either<BackupError, Boolean> {
        if (tree == null) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                appContext.contentResolver
                    .takePersistableUriPermission(tree, PERSISTED_ACCESS)
                prepareOwnFolder(tree)
                // Written last: a preference naming a folder this app could not prepare
                // would be a vault pointed somewhere it cannot write.
                settings.putString(KEY_TREE, tree.toString())
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The link is the app's own subfolder being reachable inside the tree, not the tree
     * being remembered.
     *
     * The stricter of the two readings is the right one: a grant that was revoked, a folder
     * the person deleted from a file manager and a volume that is not mounted all leave the
     * remembered `Uri` exactly as it was, and all three are a link that has fallen. None of
     * them is repaired here — what this does is read and report (design D12).
     */
    override suspend fun link(): FolderLink {
        if (storedTree() == null) return FolderLink.NONE
        return if (ownFolder() != null) FolderLink.LINKED else FolderLink.BROKEN
    }

    /**
     * The app's own subfolder as the provider describes it now, or null when nothing has
     * been pointed at and when what was pointed at cannot be reached.
     *
     * The two are one answer on purpose, exactly as they are on the desktop: to a
     * destination they are the same refusal, and what separates them for a person is
     * [FolderLink], which the screen reads.
     *
     * **It never creates anything.** A folder that has gone must fail rather than be built
     * again somewhere it never was (design D9).
     */
    internal suspend fun ownFolder(): OwnFolder? = withContext(Dispatchers.IO) {
        val tree = storedTree() ?: return@withContext null
        val documentId = Either.catch { findOwnFolder(tree) }.getOrNull()
        documentId?.let { OwnFolder(tree = tree, documentId = it) }
    }

    private fun storedTree(): Uri? = settings.getStringOrNull(KEY_TREE)?.let(Uri::parse)

    /**
     * The document id of the app's own subfolder inside [tree], or null when the tree was
     * read and does not hold one.
     *
     * @throws IOException when the tree could not be read at all, which is not the same
     * answer and must never be turned into one.
     */
    private fun findOwnFolder(tree: Uri): String? = appContext.contentResolver
        .childrenOf(tree, DocumentsContract.getTreeDocumentId(tree))
        .firstOrNull { it.isDirectory && it.name == BACKUP_FOLDER_NAME }
        ?.documentId

    /**
     * Makes sure the app's own subfolder is inside [tree], and settles what to do when the
     * provider says it was already there.
     *
     * The order is the whole of it. A tree that cannot be read throws before anything is
     * created, so a listing that never happened never becomes a second folder. A tree that
     * reads as not holding one is where a folder is created — and the name the provider
     * gives back is checked, because that is the only evidence available that a listing
     * which looked complete was not: a provider asked for a name already in use creates the
     * folder under another one. When that happens the duplicate goes and the real folder is
     * looked for again; a second reading that still cannot see it refuses, leaving the
     * preference unwritten and the archive untouched.
     */
    private fun prepareOwnFolder(tree: Uri) {
        val resolver = appContext.contentResolver
        if (findOwnFolder(tree) != null) return

        val root = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val created = DocumentsContract.createDocument(
            resolver,
            root,
            Document.MIME_TYPE_DIR,
            BACKUP_FOLDER_NAME,
        ) ?: throw IOException("The folder for backups could not be made")

        if (resolver.documentAt(created)?.name == BACKUP_FOLDER_NAME) return

        // Renamed, which means the name was taken and the listing above was wrong. The
        // duplicate is empty and this app made it a moment ago, so removing it takes
        // nothing away from anybody; a removal that fails leaves an empty folder behind,
        // which is litter and not a split archive, and is not worth abandoning the
        // recovery over.
        Either.catch { DocumentsContract.deleteDocument(resolver, created) }

        if (findOwnFolder(tree) != null) return
        throw IOException("The folder for backups is there and the provider will not list it")
    }

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
 * The app's own subfolder, as the two handles that address it: the tree the grant is held
 * over, and the document id inside that tree.
 *
 * Both are needed together and neither is useful alone — `DocumentsContract` builds every
 * `Uri` for a child from the tree it was reached through, and a document `Uri` built any
 * other way is one the grant does not cover.
 */
internal class OwnFolder(val tree: Uri, val documentId: String) {

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
