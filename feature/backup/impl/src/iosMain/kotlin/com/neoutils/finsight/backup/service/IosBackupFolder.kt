@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.vault.service.BackupFolder
import com.neoutils.finsight.domain.vault.service.FolderIdentity
import com.neoutils.finsight.domain.vault.service.FolderLink
import com.neoutils.finsight.domain.vault.service.folderIdentity
import com.neoutils.finsight.extension.PlatformContext
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
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
import platform.posix.memcpy

/**
 * iOS's half of design D4's machine: the document picker opened on folders, and a bookmark
 * of what was pointed at. The copies go straight into that folder — there is no subfolder of
 * the app's own inside it.
 *
 * **Only the bookmark is written down, and it is written as bytes** (task 11.5). A url the
 * picker grants carries its permission out of band from the text of its address — the
 * sandbox extension token is not part of `absoluteString`, so a url that made the round trip
 * through text would resolve to something that looks identical and opens nothing (design
 * D2). Nothing here ever asks a folder url for its `path` or its `absoluteString`: the
 * bookmark goes into [NSUserDefaults] as [NSData], every file operation below uses
 * Foundation's url-taking API rather than its path-taking one, and the one member that hands
 * a folder out is `internal` and hands it to a lambda that cannot outlive the access
 * ([withChosenFolder]).
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
 * there: only listing the chosen folder itself settles it, so a scope that expired while
 * resolution went on working reads [FolderLink.BROKEN] like any other loss, and the screen
 * says so (design D12). Nothing here repairs anything or moves anybody's choice.
 *
 * **One more bookmark, beside the one [point] writes.** [pointAt] shifts whatever
 * [KEY_BOOKMARK] held into [KEY_BOOKMARK_PREVIOUS] the instant it is about to be overwritten
 * by a genuinely different bookmark — never on a first-ever pointing, and never on a
 * re-point at the folder already remembered (task 11.10). [previous] reads that second key
 * with everything else this class already knows how to do, which is what lets a carry
 * offered right after a folder change still read the folder being left, through
 * [com.neoutils.finsight.domain.vault.VaultDestinations.rungFor] — even though the app's one
 * *current* bookmark has already moved on to naming the new folder by the time the offer is
 * answered.
 *
 * **[resolve], [remember] and [withChosenFolder] all read and write [key], never
 * [KEY_BOOKMARK] directly.** [withChosenFolder] rewrites a stale bookmark in place through
 * [remember] — a folder addressed differently now, not a folder somebody chose — and the
 * previous-token reader runs that same path over its own bookmark whenever
 * [com.neoutils.finsight.domain.vault.VaultMigration] reads the folder being left. Hardwiring
 * either call to [KEY_BOOKMARK] would let a stale rewrite on the previous instance silently
 * clobber the current folder's bookmark instead of its own.
 */
class IosBackupFolder private constructor(
    /**
     * Where the bookmark is kept — the same store every other preference of this install is
     * in, since `Settings()` on Apple platforms is `NSUserDefaults.standardUserDefaults`.
     * It is addressed directly rather than through `Settings` because `Settings` speaks
     * strings and this is bytes, and turning these particular bytes into text and back is
     * the one thing design D2 is written to prevent anybody doing by habit.
     */
    private val defaults: NSUserDefaults,
    /**
     * How a folder is put to the person — the system picker by default, and the only part
     * of pointing at one that cannot be exercised anywhere. See [chooseFolderWithPicker].
     */
    private val choose: suspend (PlatformContext) -> NSURL?,
    /** Which defaults key this instance reads and writes — [KEY_BOOKMARK] or [KEY_BOOKMARK_PREVIOUS]. */
    private val key: String,
) : BackupFolder {

    constructor(
        defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
        choose: suspend (PlatformContext) -> NSURL? = { chooseFolderWithPicker(it) },
    ) : this(defaults, choose, KEY_BOOKMARK)

    override val isOffered = true

    override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> =
        pointAt(choose(context))

    /**
     * Everything pointing at a folder means, once one has been pointed at.
     *
     * It is apart from [point] because the picker is the only half that needs a person in
     * front of the screen, and every rule is in this half: what a closed picker means, and
     * when the bookmark is written.
     *
     * The bookmark is written last, after the folder is confirmed listable, for the reason
     * the other two platforms write their token last — a vault pointed at a folder it could
     * not read would be a vault that stops writing at the next trigger.
     */
    internal suspend fun pointAt(chosen: NSURL?): Either<BackupError, Boolean> {
        if (chosen == null) return false.right()

        return withContext(Dispatchers.Default) {
            chosen.withAccess {
                if (!chosen.lists()) {
                    BackupError.EXPORT_FAILED.left()
                } else {
                    val before = defaults.dataForKey(key)
                    if (remember(chosen)) {
                        shiftToPreviousIfChanged(before)
                        true.right()
                    } else {
                        BackupError.EXPORT_FAILED.left()
                    }
                }
            }
        }
    }

    /**
     * Keeps the bookmark [pointAt] just overwrote reachable under [KEY_BOOKMARK_PREVIOUS]
     * (task 11.10). It runs only on the instance that owns [KEY_BOOKMARK] — the
     * previous-token reader's own [pointAt] is never actually called — and it shifts nothing
     * when there was no bookmark remembered yet, or when [remember] just wrote the same
     * bookmark back: a first-ever pointing and a re-point at the same folder both touch
     * nothing.
     */
    private fun shiftToPreviousIfChanged(before: NSData?) {
        if (key != KEY_BOOKMARK || before == null) return
        val after = defaults.dataForKey(key) ?: return
        if (!before.toByteArray().contentEquals(after.toByteArray())) {
            defaults.setObject(before, forKey = KEY_BOOKMARK_PREVIOUS)
        }
    }

    /**
     * The link is the chosen folder answering a listing, not the bookmark resolving.
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
        if (defaults.dataForKey(key) == null) return@withContext FolderLink.NONE
        val reached = withChosenFolder(BackupError.EXPORT_FAILED) { true.right() }
        if (reached.isRight()) FolderLink.LINKED else FolderLink.BROKEN
    }

    /**
     * The bookmark's own bytes, fingerprinted — never the bytes themselves, and never the
     * url they resolve to, whose `path` or `absoluteString` nothing here ever asks for
     * either (design D2).
     *
     * **This is the one platform where that answer moves on its own.** [withChosenFolder]
     * rewrites the bookmark in place when a resolution comes back stale — the same folder,
     * addressed differently now — so two readings of a folder nobody has re-pointed at can
     * still fingerprint apart. Nothing here can promise otherwise; see [FolderIdentity]'s
     * own comment for which comparison that leaves safe to make.
     */
    override val identity: FolderIdentity?
        get() = defaults.dataForKey(key)?.let { folderIdentity(it.toByteArray()) }

    /**
     * The chosen folder's own `lastPathComponent`, or null when nothing is pointed at or the
     * bookmark will not resolve to something that still lists — the same reading [link]
     * takes, so nothing is offered for a folder that is not actually there.
     *
     * **This is the one platform that still answers a segment rather than a location, and
     * that is a gap rather than a rule.** The other two say where the folder is, because a
     * name alone cannot tell two folders called `Backups` apart — see
     * [BackupFolder.displayPath]. What stops this one is not design D2, which forbids
     * addressing the destination *through* text and is untouched by text on a screen: it is
     * that a url here is a container path, and the honest half of it — `iCloud Drive ›
     * Documents › Backups` as the Files app writes it — is not a slice of `pathComponents`.
     * `/private/var/mobile/Library/Mobile Documents/com~apple~CloudDocs/…` is where it
     * really is and is not what anybody would recognise, so putting it up would answer the
     * question with noise.
     *
     * Deciding what a container reads as belongs with somebody who can watch it on a device,
     * which is where this rung's every other question was settled (Q1). Until then the
     * segment stands, and it is the same segment it always was.
     *
     * `lastPathComponent` is the one property this reads off the url. Everything else here
     * goes on refusing `path` and `absoluteString` (design D2), and the scan that enforces
     * that is `ScopedUrlNeverTextTest`.
     */
    override suspend fun displayPath(): String? = withContext(Dispatchers.Default) {
        withChosenFolder(BackupError.EXPORT_FAILED) { url ->
            url.lastPathComponent?.right() ?: BackupError.EXPORT_FAILED.left()
        }.getOrNull()
    }

    /**
     * Runs [block] against the folder somebody pointed at, with access to it claimed for
     * exactly that long.
     *
     * **The shape is the guarantee.** The folder is resolved, claimed, handed to a lambda
     * and released in a `finally`, so no caller can hold a url past its access or forget to
     * balance one (task 11.4) — and no caller is ever given a url it could write down,
     * because it is given one only inside a call that is about to end (task 11.5).
     *
     * **It refuses on everything**: nothing was pointed at, the bookmark will not resolve,
     * the folder will not list. To a destination those are one flat *I cannot*; what
     * separates them for a person is [FolderLink], which the screen reads.
     *
     * **A folder that answers a listing is a folder that is there**, which is what lets the
     * destination beside this treat an empty answer as an empty folder rather than as design
     * D9's forbidden sentence: by the time a copy is written, listed or removed, this has
     * already had a complete directory read out of this provider, over this scope, a moment
     * earlier.
     */
    internal fun <T> withChosenFolder(
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

            if (!resolved.url.lists()) otherwise.left() else block(resolved.url)
        }
    }

    /**
     * The folder somebody pointed at, as the bookmark resolves it now, or null when nothing
     * was pointed at and when the bookmark will not resolve.
     */
    private fun resolve(): Resolved? {
        val bookmark = defaults.dataForKey(key) ?: return null

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

        defaults.setObject(bookmark, forKey = key)
        true
    }

    /** [BackupFolder.forgetPrevious] — see the class comment for the two occasions this runs. */
    override fun forgetPrevious() = defaults.removeObjectForKey(KEY_BOOKMARK_PREVIOUS)

    private class Resolved(val url: NSURL, val isStale: Boolean)

    companion object {

        /**
         * iOS's own key. Each platform remembers its own kind of token — a bookmark here, a
         * tree `Uri` on Android, a path on the desktop — and no install ever reads another
         * platform's.
         */
        private const val KEY_BOOKMARK = "backup_vault_folder_bookmark"

        /**
         * The bookmark [pointAt] shifted aside on its last change, beside [KEY_BOOKMARK]
         * rather than instead of it — both are held at once so a carry offered right after a
         * folder change can still read the one being left (task 11.10).
         */
        private const val KEY_BOOKMARK_PREVIOUS = "backup_vault_folder_bookmark_previous"

        /**
         * No options, which on iOS is the only creation that grants anything: a bookmark
         * made without `WithoutImplicitSecurityScope` embeds an implicit ephemeral security
         * scope automatically, and that embedded scope is the whole of what lets a later
         * launch reach the folder again.
         */
        private const val IMPLICIT_SECURITY_SCOPE: ULong = 0uL

        /**
         * A read-only reader of the bookmark [pointAt] most recently shifted aside —
         * everything [IosBackupFolder] already knows how to do, over [KEY_BOOKMARK_PREVIOUS]
         * instead of [KEY_BOOKMARK] (task 11.10). Its own [point]/[pointAt] are never meant
         * to be called — [choose] answers null unconditionally, so a call resolves to
         * *nothing chosen* rather than doing anything to either key.
         */
        fun previous(defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults): IosBackupFolder =
            IosBackupFolder(defaults, choose = { null }, key = KEY_BOOKMARK_PREVIOUS)
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
 * A plain copy of the bytes, for [IosBackupFolder.identity] to fingerprint — the one reason
 * this exists, since every other reader of a bookmark here reads it as [NSData] and hands it
 * straight to Foundation.
 */
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)

    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
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
