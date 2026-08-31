@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithoutImplicitStartAccessing
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeFolder

/**
 * iOS's half of design D4's machine: the document picker opened on folders, a bookmark of
 * what was pointed at, and the app's own subfolder made inside it.
 *
 * **Only the bookmark is written down, and it is written as bytes** (task 11.5). A url the
 * picker grants carries its permission out of band from the text of its address — the
 * sandbox extension token is not part of `absoluteString`, so a url that made the round trip
 * through text would resolve to something that looks identical and opens nothing (design
 * D2). Nothing here ever asks a folder url for its `path` or its `absoluteString`: the
 * bookmark goes into [NSUserDefaults] as [NSData], every file operation below uses
 * Foundation's url-taking API rather than its path-taking one, and the one member that hands
 * a folder out is `internal` and hands it to a lambda that cannot outlive the access
 * ([withOwnFolder]).
 *
 * **Creation takes no options, because on iOS there are none to take.**
 * `NSURLBookmarkCreationWithSecurityScope` is macOS's — `API_UNAVAILABLE(ios, watchos,
 * tvos)` at `NSURL.h:425`, and present in Kotlin/Native's `platform.Foundation` only because
 * cinterop emits option constants whether or not the platform implements them, so reaching
 * for it would compile and do nothing. What iOS has instead is the *implicit* ephemeral
 * security scope that every bookmark made without `WithoutImplicitSecurityScope` carries
 * automatically, and that is what this depends on. `MinimalBookmark` is left off: it only
 * shrinks the data, and there is no reason to vary the one thing spike Q1 is measuring.
 *
 * **How long that scope lasts is not documented, in either direction.** Apple's *"valid
 * until reboot at the latest"* is an upper bound and not a promise, and their own developer
 * support has said the lower bound *"isn't actually well defined… nor something that can
 * really be relied on"*. So nothing here assumes the link survives and nothing here assumes
 * it does not: the folder is read at every operation and the answer is reported. What is
 * settled is that this path *will* be reached — a bookmark into a removable volume is known
 * to break across a remount — and design D16 keeps the app from judging the provider, so
 * being reached is inside the design rather than a defect of it.
 *
 * **The real option is on resolution, and it is taken deliberately.**
 * `NSURLBookmarkResolutionWithoutImplicitStartAccessing` (`NSURL.h:434`, iOS 14.2) suppresses
 * the access that resolution otherwise starts on its own. Without it the pair below is not a
 * pair at all: resolution would open a grant with no documented `stop` to match it, and the
 * `start` this code calls afterwards would either be a second claim leaked on every operation
 * or a claim released against an access it never made. With it, `start` and
 * `stopAccessingSecurityScopedResource` are the only two calls in play and they bracket
 * exactly one operation, in a `finally` (task 11.4). A bookmark that comes back stale is
 * written again on the spot, while access is held — it is the same folder, addressed as it
 * stands now.
 *
 * **The verdict on the link is a reading of the folder and never of the bookmark.** A
 * bookmark that resolves is not access, and access that is claimed is not a folder that is
 * there: only listing the app's own subfolder settles it, so a scope that expired while
 * resolution went on working reads [FolderLink.BROKEN] like any other loss, and the screen
 * says so (design D12). Nothing here repairs anything or moves anybody's choice.
 *
 * **Only this creates the app's own subfolder, and only with a person in front of the
 * screen.** Nothing on the writing path ever makes it (design D9): a folder that has gone
 * away must fail rather than be built again somewhere it never was, which on iOS is a
 * provider that has been signed out of or an external volume that is no longer attached.
 */
class IosBackupFolder(
    /**
     * Where the bookmark is kept — the same store every other preference of this install is
     * in, since `Settings()` on Apple platforms is `NSUserDefaults.standardUserDefaults`.
     * It is addressed directly rather than through `Settings` because `Settings` speaks
     * strings and this is bytes, and turning these particular bytes into text and back is
     * the one thing design D2 is written to prevent anybody doing by habit.
     */
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    /**
     * How a folder is put to the person — the system picker by default, and the only part
     * of pointing at one that cannot be exercised anywhere. See [chooseFolderWithPicker].
     */
    private val choose: suspend (PlatformContext) -> NSURL? = { chooseFolderWithPicker(it) },
) : BackupFolder {

    override val isOffered = true

    override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> =
        pointAt(choose(context))

    /**
     * Everything pointing at a folder means, once one has been pointed at.
     *
     * It is apart from [point] because the picker is the only half that needs a person in
     * front of the screen, and every rule is in this half: what a closed picker means, which
     * folder is made, and when the bookmark is written.
     *
     * The bookmark is written last, after the subfolder is there, for the reason the other
     * two platforms write their token last — a vault pointed at a folder it could not
     * prepare would be a vault that stops writing at the next trigger.
     */
    internal suspend fun pointAt(chosen: NSURL?): Either<BackupError, Boolean> {
        if (chosen == null) return false.right()

        return withContext(Dispatchers.Default) {
            chosen.withAccess {
                prepareOwnFolder(chosen).flatMap {
                    if (remember(chosen)) true.right() else BackupError.EXPORT_FAILED.left()
                }
            }
        }
    }

    /**
     * The link is the app's own subfolder answering a listing, not the bookmark resolving.
     *
     * The strictest of the readings is the right one, and on iOS there are three that can
     * part company: a bookmark that no longer resolves, one that resolves to a url access
     * cannot be claimed on, and one that resolves and opens onto a folder whose contents are
     * gone. A provider signed out of, a folder the person deleted from Files, an external
     * volume unplugged and an implicit scope that did not survive a reboot land on one or
     * another of those, and all of them are a link that has fallen. None is repaired here —
     * what this does is read and report (design D12).
     */
    override suspend fun link(): FolderLink = withContext(Dispatchers.Default) {
        if (defaults.dataForKey(KEY_BOOKMARK) == null) return@withContext FolderLink.NONE
        val reached = withOwnFolder(BackupError.EXPORT_FAILED) { true.right() }
        if (reached.isRight()) FolderLink.LINKED else FolderLink.BROKEN
    }

    /**
     * Runs [block] against the app's own subfolder inside the folder somebody pointed at,
     * with access to it claimed for exactly that long.
     *
     * **The shape is the guarantee.** The folder is resolved, claimed, handed to a lambda
     * and released in a `finally`, so no caller can hold a url past its access or forget to
     * balance one (task 11.4) — and no caller is ever given a url it could write down,
     * because it is given one only inside a call that is about to end (task 11.5).
     *
     * **It never creates anything**, and it refuses on everything: nothing was pointed at,
     * the bookmark will not resolve, the folder will not list. To a destination those are
     * one flat *I cannot*; what separates them for a person is [FolderLink], which the
     * screen reads.
     *
     * **A folder that answers a listing is a folder that is there**, which is what lets the
     * destination beside this treat an empty answer as an empty folder rather than as design
     * D9's forbidden sentence: by the time a copy is written, listed or removed, this has
     * already had a complete directory read out of this provider, over this scope, a moment
     * earlier.
     */
    internal fun <T> withOwnFolder(
        otherwise: BackupError,
        block: (NSURL) -> Either<BackupError, T>,
    ): Either<BackupError, T> {
        val resolved = resolve() ?: return otherwise.left()

        return resolved.url.withAccess {
            // Stale is the file system saying *this is the folder, addressed differently
            // now* — a rename, a move, a provider that reissued its ids. Writing the
            // bookmark again here is what keeps the next launch pointed at it; failing to
            // is not a failure to read, so the answer below does not depend on it.
            if (resolved.isStale) remember(resolved.url)

            val own = resolved.url.URLByAppendingPathComponent(BACKUP_FOLDER_NAME, true)
            if (own == null || !own.lists()) otherwise.left() else block(own)
        }
    }

    /**
     * Makes sure the app's own subfolder is inside [chosen], creating it only when a listing
     * of the chosen folder says it is not there.
     *
     * Re-pointing at a folder that already holds one must find the archive rather than build
     * a second place for it (design D4), and the order is what guarantees that: a chosen
     * folder that cannot be listed at all fails before anything is created, so a reading
     * that never happened never becomes a folder somewhere it never was.
     *
     * Creation is coordinated like every other write here, and asks for intermediate
     * directories so that a subfolder which appeared between the reading and the writing is
     * an outcome rather than an error.
     */
    private fun prepareOwnFolder(chosen: NSURL): Either<BackupError, Unit> {
        if (!chosen.lists()) return BackupError.EXPORT_FAILED.left()

        val own = chosen.URLByAppendingPathComponent(BACKUP_FOLDER_NAME, true)
            ?: return BackupError.EXPORT_FAILED.left()
        if (own.lists()) return Unit.right()

        return coordinateWriting(own, NO_WRITING_OPTIONS, BackupError.EXPORT_FAILED) { url ->
            memScoped {
                val failure = alloc<ObjCObjectVar<NSError?>>()
                val created = NSFileManager.defaultManager.createDirectoryAtURL(
                    url = url,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = failure.ptr,
                )
                if (created) {
                    Unit.right()
                } else {
                    failure.value.toBackupError(BackupError.EXPORT_FAILED).left()
                }
            }
        }
    }

    /**
     * The folder somebody pointed at, as the bookmark resolves it now, or null when nothing
     * was pointed at and when the bookmark will not resolve.
     */
    private fun resolve(): Resolved? {
        val bookmark = defaults.dataForKey(KEY_BOOKMARK) ?: return null

        return memScoped {
            val stale = alloc<BooleanVar>()
            val url = NSURL.URLByResolvingBookmarkData(
                bookmarkData = bookmark,
                options = NSURLBookmarkResolutionWithoutImplicitStartAccessing,
                relativeToURL = null,
                bookmarkDataIsStale = stale.ptr,
                error = null,
            ) ?: return@memScoped null

            Resolved(url = url, isStale = stale.value)
        }
    }

    /** Writes [folder] down as a bookmark, answering whether one could be made of it. */
    private fun remember(folder: NSURL): Boolean = memScoped {
        val bookmark: NSData = folder.bookmarkDataWithOptions(
            options = IMPLICIT_SECURITY_SCOPE,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = null,
        ) ?: return@memScoped false

        defaults.setObject(bookmark, forKey = KEY_BOOKMARK)
        true
    }

    private class Resolved(val url: NSURL, val isStale: Boolean)

    private companion object {

        /**
         * iOS's own key. Each platform remembers its own kind of token — a bookmark here, a
         * tree `Uri` on Android, a path on the desktop — and no install ever reads another
         * platform's.
         */
        const val KEY_BOOKMARK = "backup_vault_folder_bookmark"

        /**
         * No options, which on iOS is the only creation that grants anything: a bookmark
         * made without `WithoutImplicitSecurityScope` embeds an implicit ephemeral security
         * scope automatically, and that embedded scope is the whole of what lets a later
         * launch reach the folder again.
         */
        const val IMPLICIT_SECURITY_SCOPE: ULong = 0uL
    }
}

/**
 * Claims access to a security-scoped url for the length of [block], and gives it up
 * afterwards whatever happened (task 11.4).
 *
 * `stop` is called only against a `start` that answered true, which is Foundation's own
 * pairing: a false is *nothing was claimed*, and giving up a claim that was never made
 * unbalances a count the system keeps for the process.
 */
private inline fun <T> NSURL.withAccess(block: () -> T): T {
    val claimed = startAccessingSecurityScopedResource()
    try {
        return block()
    } finally {
        if (claimed) {
            stopAccessingSecurityScopedResource()
        }
    }
}

/**
 * Whether this url answers a complete directory listing right now.
 *
 * It is the one question every operation in this rung is built on, and it is asked as a
 * listing rather than as an attribute because a listing is the thing that has to work: a url
 * that describes itself as a directory while its provider will not enumerate it is a link
 * that has fallen, and a reading that stopped at the description would call it linked.
 */
internal fun NSURL.lists(): Boolean = NSFileManager.defaultManager.contentsOfDirectoryAtURL(
    url = this,
    includingPropertiesForKeys = null,
    options = 0uL,
    error = null,
) != null

/**
 * Runs [accessor] as a coordinated write over [url], and answers what it answered.
 *
 * Coordination is what tells the rest of the system that this app is about to change a file
 * in a folder it does not own alone (task 11.4). A folder the person chose is reachable from
 * Files, from a sync client and from whatever else they have installed, and a write announced
 * through [NSFileCoordinator] waits for those readers and makes them wait for it — which for
 * a database copy is the difference between a file another process is halfway through
 * reading and one it never saw in that state.
 *
 * The url handed to [accessor] is the coordinator's and not the one asked for: it hands back
 * an updated address when the item moved while the write was waiting, and writing to the
 * original would then be writing where the item no longer is.
 */
internal fun <T> coordinateWriting(
    url: NSURL,
    options: ULong,
    otherwise: BackupError,
    accessor: (NSURL) -> Either<BackupError, T>,
): Either<BackupError, T> = memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    var outcome: Either<BackupError, T> = otherwise.left()

    NSFileCoordinator(null).coordinateWritingItemAtURL(
        url = url,
        options = options,
        error = failure.ptr,
    ) { granted ->
        outcome = granted?.let(accessor) ?: otherwise.left()
    }

    // Consulted only on a refusal, and it is the coordination's own: an accessor that ran
    // and failed has already said why, in the error of the operation that actually failed.
    outcome.mapLeft { fallback -> failure.value.toBackupError(fallback) }
}

/**
 * Runs [accessor] as a coordinated read over [url], and answers what it answered.
 *
 * The waiting is the point, and so is what the wait is for: a coordinated read of an item a
 * provider has not materialised on this device is what makes the provider produce it, so a
 * copy that lives in the cloud is downloaded here instead of being read as a file of no
 * bytes.
 */
internal fun <T> coordinateReading(
    url: NSURL,
    otherwise: BackupError,
    accessor: (NSURL) -> Either<BackupError, T>,
): Either<BackupError, T> = memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    var outcome: Either<BackupError, T> = otherwise.left()

    NSFileCoordinator(null).coordinateReadingItemAtURL(
        url = url,
        options = 0uL,
        error = failure.ptr,
    ) { granted ->
        outcome = granted?.let(accessor) ?: otherwise.left()
    }

    outcome.mapLeft { fallback -> failure.value.toBackupError(fallback) }
}

/**
 * Puts the system picker up over folders and answers the one that was pointed at, or null
 * when the person closed it.
 *
 * It is a function beside the class rather than a method on it because it is the one half of
 * pointing at a folder that needs a person in front of the screen, and the half that cannot
 * be exercised anywhere: no test on any platform drives a system picker. What a chosen
 * folder *means* is [IosBackupFolder.pointAt], and that is where the rules are.
 *
 * `asCopy` is deliberately absent, which is what makes this different from both dialogs in
 * [IosBackupFileService]: those two want a copy in the sandbox, and this one wants the
 * original — a copy of a folder is a snapshot of it, and the whole point of the rung is
 * writing into a place that outlives the app.
 */
internal suspend fun chooseFolderWithPicker(context: PlatformContext): NSURL? =
    context.awaitPickedUrls(BackupError.EXPORT_FAILED) {
        UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
    }.getOrNull()?.firstOrNull()

/** The plain coordinated write: not a deletion, not a move, not a replacement. */
private const val NO_WRITING_OPTIONS: ULong = 0uL
