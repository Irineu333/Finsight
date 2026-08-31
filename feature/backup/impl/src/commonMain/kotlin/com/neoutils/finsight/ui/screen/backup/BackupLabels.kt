@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.restore.FileOrigin
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.label
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_age_day
import com.neoutils.finsight.resources.backup_age_days
import com.neoutils.finsight.resources.backup_age_hours
import com.neoutils.finsight.resources.backup_age_minutes
import com.neoutils.finsight.resources.backup_age_month
import com.neoutils.finsight.resources.backup_age_months
import com.neoutils.finsight.resources.backup_age_now
import com.neoutils.finsight.resources.backup_copies_many
import com.neoutils.finsight.resources.backup_copies_none
import com.neoutils.finsight.resources.backup_copies_one
import com.neoutils.finsight.resources.backup_confirm_origin_unknown
import com.neoutils.finsight.resources.backup_destination_app
import com.neoutils.finsight.resources.backup_destination_folder
import com.neoutils.finsight.resources.backup_destination_folder_named
import com.neoutils.finsight.resources.backup_platform_android
import com.neoutils.finsight.resources.backup_platform_desktop
import com.neoutils.finsight.resources.backup_platform_ios
import com.neoutils.finsight.resources.backup_retention_everything
import com.neoutils.finsight.resources.backup_retention_five
import com.neoutils.finsight.resources.backup_retention_ten
import com.neoutils.finsight.resources.backup_retention_twenty
import com.neoutils.finsight.resources.backup_size_bytes
import com.neoutils.finsight.resources.backup_size_kb
import com.neoutils.finsight.resources.backup_size_mb
import com.neoutils.finsight.util.stringUiText
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * The words the two backup screens put the same values in.
 *
 * They are here rather than beside either screen because both say them: the tile that leads
 * to the copies and the header of the copies themselves count the same files, and a
 * disagreement between the two would be a disagreement about what is in the destination.
 */

/** How many copies are in the destination, with none said as none rather than as zero. */
@Composable
fun copiesLabel(count: Int): String = when (count) {
    0 -> stringResource(Res.string.backup_copies_none)
    1 -> stringResource(Res.string.backup_copies_one)
    else -> stringResource(Res.string.backup_copies_many, count)
}

/**
 * How much room a file takes, in whole units.
 *
 * No decimals, and that is a decision rather than a rounding: the separator between a whole
 * part and a fraction is a comma in one of the two languages this app ships in and a full
 * stop in the other, and none of the three places this number appears is a place where a
 * tenth of a megabyte changes anybody's mind.
 */
@Composable
fun sizeLabel(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> stringResource(
        Res.string.backup_size_mb,
        (bytes + BYTES_PER_MB / 2) / BYTES_PER_MB,
    )

    bytes >= BYTES_PER_KB -> stringResource(
        Res.string.backup_size_kb,
        (bytes + BYTES_PER_KB / 2) / BYTES_PER_KB,
    )

    else -> stringResource(Res.string.backup_size_bytes, bytes)
}

/**
 * How far back a copy reaches, in the unit a person answers the question in.
 *
 * It is the one thing about a copy that decides anything: choosing between two of them is
 * choosing how much of what was typed since is lost, and "12 ago, 09:15" does not say that
 * — it has to be worked out against today. The exact stamp stays where it is; this is the
 * reading of it.
 *
 * The scale is coarse on purpose and coarsens as it goes: minutes while the copy is minutes
 * old, then hours, then days, then months. Nobody restoring a copy from March cares whether
 * it is 94 days or 96, and the row already carries the date for whoever does.
 *
 * A copy stamped ahead of now — a device whose clock moved, a file written by another
 * install — reads as [Res.string.backup_age_now] rather than as a negative span. It is the
 * closest true thing that can be said: there is nothing between it and the present.
 */
@Composable
fun ageLabel(instant: Instant, now: Instant): String {
    val elapsed = now - instant
    val days = elapsed.inWholeDays

    return when {
        elapsed < 1.minutes -> stringResource(Res.string.backup_age_now)

        elapsed < 1.hours -> stringResource(
            Res.string.backup_age_minutes,
            elapsed.inWholeMinutes,
        )

        elapsed < 1.days -> stringResource(Res.string.backup_age_hours, elapsed.inWholeHours)
        days == 1L -> stringResource(Res.string.backup_age_day)
        days < DAYS_PER_MONTH -> stringResource(Res.string.backup_age_days, days)
        days < 2 * DAYS_PER_MONTH -> stringResource(Res.string.backup_age_month)
        else -> stringResource(Res.string.backup_age_months, days / DAYS_PER_MONTH)
    }
}

/** How long the vault waits before it looks for a reason to take another copy. */
@Composable
fun intervalLabel(interval: VaultInterval): String = stringUiText(interval.label)

@Composable
fun retentionLabel(retention: BackupRetention): String = when (retention) {
    BackupRetention.FIVE -> stringResource(Res.string.backup_retention_five)
    BackupRetention.TEN -> stringResource(Res.string.backup_retention_ten)
    BackupRetention.TWENTY -> stringResource(Res.string.backup_retention_twenty)
    BackupRetention.EVERYTHING -> stringResource(Res.string.backup_retention_everything)
}

/**
 * Which device wrote a file, when the file names one this build knows; the raw stamp when
 * it names one this build does not; and unknown origin when it names none at all. The three
 * cases are different: only the last is a file that said nothing.
 *
 * Two sheets say it about the same stamp — the confirmation before a restore, and the sheet
 * that describes a kept copy — so it is said once. A second reading of the same field would
 * be a second way of naming a platform.
 */
@Composable
fun originLabel(origin: FileOrigin?): String = when (origin?.platform) {
    BackupPlatform.ANDROID -> stringResource(Res.string.backup_platform_android)
    BackupPlatform.DESKTOP -> stringResource(Res.string.backup_platform_desktop)
    BackupPlatform.IOS -> stringResource(Res.string.backup_platform_ios)
    null -> origin?.platformId ?: stringResource(Res.string.backup_confirm_origin_unknown)
}

/**
 * Which device wrote the file, and which build of the app — the two halves of one answer,
 * said in the one row both sheets give the file's origin.
 *
 * The sheet about a kept copy and the confirmation before a restore describe the same stamp,
 * so a second version of this would be a second way of naming the same file.
 */
@Composable
fun originWithVersion(origin: FileOrigin?): String {
    val where = originLabel(origin)

    // A build that states no version of its own stamps none, and none is shown.
    return if (origin != null && origin.appVersion.isNotBlank()) {
        "$where · v${origin.appVersion}"
    } else {
        where
    }
}

/**
 * Where the copies are, said as the place a person recognises rather than as a path — the
 * consequence of choosing it is what the coverage sentence beside it is for.
 *
 * **[folderName] is the folder's own name, and it replaces the generic sentence once a
 * caller has one to give.** It is never a path and never anything that could reopen the
 * folder (design D2) — see
 * [com.neoutils.finsight.ui.screen.backup.service.BackupFolder.displayName]. Left null, the
 * generic sentence stands: not every caller has read the destination, and a settings row that
 * names the rung without waiting on a folder to answer is a decision of its own (see the
 * backup screen's own tile).
 */
@Composable
fun destinationLabel(destination: VaultDestination, folderName: String? = null): String =
    when (destination) {
        VaultDestination.APP_STORAGE -> stringResource(Res.string.backup_destination_app)
        VaultDestination.USER_FOLDER -> folderName
            ?.let { stringResource(Res.string.backup_destination_folder_named, it) }
            ?: stringResource(Res.string.backup_destination_folder)
    }

/**
 * What a month is worth when a span is being said out loud: a round thirty days, and not a
 * calendar. No date arithmetic passes through it — it is the width of the bucket "months
 * ago" and the divisor of a rate stated per month, and the exact stamp is on the row beside
 * it.
 */
internal const val DAYS_PER_MONTH = 30L

private const val BYTES_PER_KB = 1_024L
private const val BYTES_PER_MB = 1_024L * 1_024L
