package com.neoutils.finsight.backup.service

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/**
 * Reading a tree the person pointed at, with `DocumentsContract` and nothing on top of it.
 *
 * **`DocumentFile` is what this exists instead of** (task 11.2). That class answers one
 * question per call — a name, a size, a date — and each answer is a separate query through
 * the provider, so listing a folder of *n* copies costs `1 + 3n` round trips and the count
 * grows with the archive. One query with the whole projection costs one, and the columns
 * are already in the cursor the listing had to open anyway.
 *
 * **A cursor is trusted only when it says it is finished.** [childrenOf] refuses a query it
 * cannot believe rather than answering a short list, because everything above reasons from
 * a listing: the history would say *no copies yet* over an archive that is sitting right
 * there, and retention would count from zero (design D9). The three ways a provider says it
 * is not finished are all here — no cursor at all, `EXTRA_LOADING`, and `EXTRA_ERROR` —
 * and a fourth, the empty answer that a folder which provably existed gave once after a
 * reboot, is judged by the caller, because *empty* means different things to somebody
 * choosing a folder and to somebody taking an inventory of one.
 */

/**
 * One entry of a folder, with everything a listing needs already in hand.
 *
 * The document id is what addresses it inside the tree — never a path, and never something
 * a caller outside this module receives (design D2).
 */
internal class SafChild(
    val documentId: String,
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val sizeInBytes: Long,
)

/**
 * Everything inside [parentDocumentId], in one query.
 *
 * @throws IOException when the provider gave no cursor, or said through the cursor's own
 * extras that what it gave is still loading or failed. The answer may still be empty, and
 * what that means is the caller's to decide.
 */
internal fun ContentResolver.childrenOf(
    treeUri: Uri,
    parentDocumentId: String,
): List<SafChild> {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val cursor = query(children, CHILD_PROJECTION, null, null, null)
        ?: throw IOException("The provider answered no cursor for the folder's children")

    return cursor.use {
        it.refuseIfIncomplete()

        buildList {
            while (it.moveToNext()) {
                add(it.readChild() ?: continue)
            }
        }
    }
}

/**
 * One document, described the same way a child of a listing is, or null when the provider
 * will not describe it.
 *
 * It is asked only of a document this app has just created, and it answers the two things
 * that are worth a query at that moment: what the provider decided to call the document —
 * a name of its own making is the only evidence there is that the name asked for was
 * already taken — and what it says about the bytes that were just written into it.
 */
internal fun ContentResolver.documentAt(documentUri: Uri): SafChild? {
    val cursor = query(documentUri, CHILD_PROJECTION, null, null, null) ?: return null
    return cursor.use { if (it.moveToFirst()) it.readChild() else null }
}

/**
 * One row, or null when the provider named neither the document nor its display name —
 * which is a row nothing here could address or recognise afterwards.
 */
private fun Cursor.readChild(): SafChild? {
    val documentId = getString(DOCUMENT_ID) ?: return null
    val name = getString(DISPLAY_NAME) ?: return null
    return SafChild(
        documentId = documentId,
        name = name,
        isDirectory = getString(MIME_TYPE) == Document.MIME_TYPE_DIR,
        lastModified = getLong(LAST_MODIFIED),
        sizeInBytes = getLong(SIZE),
    )
}

/**
 * A provider that is still working, or that has already failed, says so in the extras of
 * the cursor it hands back — and a cursor in either state is a listing that is missing
 * rows nobody can name.
 */
private fun Cursor.refuseIfIncomplete() {
    if (extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
        throw IOException("The provider is still loading the folder's children")
    }
    extras.getString(DocumentsContract.EXTRA_ERROR)?.let {
        throw IOException("The provider could not read the folder's children: $it")
    }
}

private val CHILD_PROJECTION = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_LAST_MODIFIED,
    Document.COLUMN_SIZE,
)

private const val DOCUMENT_ID = 0
private const val DISPLAY_NAME = 1
private const val MIME_TYPE = 2
private const val LAST_MODIFIED = 3
private const val SIZE = 4
