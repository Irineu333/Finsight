@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import com.neoutils.finsight.database.snapshot.ArchiveCounts
import com.neoutils.finsight.domain.model.BackupPlatform
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What the screen is doing, and what it is waiting for an answer about.
 *
 * The chosen file is not in it. What the user picked lives in this app's temporary area
 * until it is either used or thrown away, and a path is not something the screen renders.
 */
data class BackupUiState(
    val isExporting: Boolean = false,
    val isVerifying: Boolean = false,
    val isRestoring: Boolean = false,
    val confirmation: RestoreConfirmation? = null,
) {

    /**
     * One flag for both entries: each of the three operations has the database's writer
     * connection to itself, and offering the other while one runs would only produce a
     * second one that has to wait.
     */
    val isBusy: Boolean get() = isExporting || isVerifying || isRestoring
}

/**
 * An approved file, as the confirmation describes it.
 *
 * It is only ever built from a verification that accepted the file — asking about a file
 * that may still be refused would hand the user a decision the app cannot yet stand
 * behind.
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
