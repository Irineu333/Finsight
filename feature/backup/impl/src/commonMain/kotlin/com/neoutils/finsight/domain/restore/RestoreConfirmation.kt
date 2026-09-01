@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.restore

import com.neoutils.finsight.database.snapshot.ArchiveCounts
import com.neoutils.finsight.database.snapshot.SnapshotOrigin
import com.neoutils.finsight.domain.model.BackupPlatform
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * An approved file, as the confirmation describes it.
 *
 * It is only ever built from a verification that accepted the file — asking about a file
 * that may still be refused would hand the user a decision the app cannot yet stand
 * behind.
 *
 * It is not the screen's, because two screens ask the same question about the same kind of
 * file: one about a file the user picked, the other about a copy the vault kept.
 */
data class RestoreConfirmation(
    val origin: FileOrigin?,
    val counts: ArchiveCounts,
    val source: RestoreSource,
)

/**
 * Where the file being restored came from — which is the whole of what the app knows about
 * whose archive it holds.
 *
 * It is not a detail of presentation. A copy this install captured itself is this install's
 * own archive at the instant stamped in it, so restoring one is a move backwards through
 * this app's history and can be said as such. A file the user picked is a file: the stamp
 * says when it was written and by which platform, and nothing in it says which device — a
 * copy exported from here and a copy exported from someone else's phone are the same four
 * columns ([FileOrigin]). The backup screen's own tile says the picked file is normally
 * *from another device*, so a sentence about how this app used to be is one the app cannot
 * stand behind there.
 *
 * A copy sitting in the destination is not proof of the first case:
 * [com.neoutils.finsight.domain.vault.ArchiveImport] puts any file that passes the gate
 * there too, and `snapshot_meta` names no install — it is the same four columns whoever
 * captured the file, so nothing inside it tells "this install's own capture" apart from
 * "a file this install brought in from a picker," and inventing an identifier to do so is
 * not this app's to add. What *is* known, because this install is the one doing it, is
 * whether *this install's own* [com.neoutils.finsight.domain.vault.ArchiveImport] is what
 * put the file there — read back by
 * [com.neoutils.finsight.domain.vault.service.isImportedFileName]. A copy another
 * install captured and a folder synced between devices then carried in without ever going
 * through this install's own import is not caught by that check and reads as [KEPT_COPY]
 * regardless; there is nothing left in a captured file that could say otherwise.
 *
 * The distinction is made where it is known — by the restore, which is handed the kept copy
 * or nothing — and never inferred from a stamp.
 */
enum class RestoreSource {

    /**
     * A copy that was either captured by this install, or reached the destination by some
     * means this install has no way to tell apart from having captured it — a folder synced
     * from another install's own vault included. See [IMPORTED_COPY] for the one case this
     * install *can* tell apart.
     */
    KEPT_COPY,

    /**
     * A copy this install brought in through its own
     * [com.neoutils.finsight.domain.vault.ArchiveImport] rather than captured. Read the same
     * as [PICKED_FILE]: the app has exactly as little standing to call this its own past.
     */
    IMPORTED_COPY,

    /** A file from the device's picker, of an archive that may never have been this one. */
    PICKED_FILE,
}

/**
 * What the file says about where it came from, or nothing at all when it carries no
 * stamp — a file captured before the stamp existed restores like any other, and the
 * screen is what calls that origin unknown.
 *
 * [platform] is null when the file names one this build does not know, which is not the
 * same as naming none: the counts and the date are still there to be shown, and
 * [platformId] is what the file itself said.
 */
data class FileOrigin(
    val platform: BackupPlatform?,
    val platformId: String,
    val appVersion: String,
    val createdAt: Instant,
)

/**
 * The stamp a file carries, as the app reads it.
 *
 * It is here rather than beside either caller because two of them read the same stamp: the
 * restore, which asks about a file before replacing the archive with it, and the sheet that
 * describes a kept copy somebody has just tapped. A second reading of the same four columns
 * would be a second answer to what platform wrote a file.
 */
internal fun SnapshotOrigin.toFileOrigin() = FileOrigin(
    platform = BackupPlatform.ofId(platform),
    platformId = platform,
    appVersion = appVersion,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)
