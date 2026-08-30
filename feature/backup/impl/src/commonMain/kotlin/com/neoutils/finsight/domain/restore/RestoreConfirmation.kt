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
)

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
