package com.neoutils.finsight.ui.screen.backup

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.label
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_copies_many
import com.neoutils.finsight.resources.backup_copies_none
import com.neoutils.finsight.resources.backup_copies_one
import com.neoutils.finsight.resources.backup_destination_app
import com.neoutils.finsight.resources.backup_destination_folder
import com.neoutils.finsight.resources.backup_retention_everything
import com.neoutils.finsight.resources.backup_retention_five
import com.neoutils.finsight.resources.backup_retention_ten
import com.neoutils.finsight.resources.backup_retention_twenty
import com.neoutils.finsight.resources.backup_size_bytes
import com.neoutils.finsight.resources.backup_size_kb
import com.neoutils.finsight.resources.backup_size_mb
import com.neoutils.finsight.util.stringUiText
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
 * Where the copies are, said as the place a person recognises rather than as a path — the
 * consequence of choosing it is what the coverage sentence beside it is for.
 */
@Composable
fun destinationLabel(destination: VaultDestination): String = when (destination) {
    VaultDestination.APP_STORAGE -> stringResource(Res.string.backup_destination_app)
    VaultDestination.USER_FOLDER -> stringResource(Res.string.backup_destination_folder)
}

private const val BYTES_PER_KB = 1_024L
private const val BYTES_PER_MB = 1_024L * 1_024L
