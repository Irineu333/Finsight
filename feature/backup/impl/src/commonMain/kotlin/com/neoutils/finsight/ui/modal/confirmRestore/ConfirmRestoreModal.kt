@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.confirmRestore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeviceUnknown
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.database.snapshot.ArchiveCounts
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.restore.FileOrigin
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_accounts
import com.neoutils.finsight.resources.backup_confirm_action
import com.neoutils.finsight.resources.backup_confirm_cancel
import com.neoutils.finsight.resources.backup_confirm_categories
import com.neoutils.finsight.resources.backup_confirm_credit_cards
import com.neoutils.finsight.resources.backup_confirm_irreversible
import com.neoutils.finsight.resources.backup_confirm_message
import com.neoutils.finsight.resources.backup_confirm_origin_unknown
import com.neoutils.finsight.resources.backup_confirm_reversible
import com.neoutils.finsight.resources.backup_confirm_title
import com.neoutils.finsight.resources.backup_confirm_transactions
import com.neoutils.finsight.resources.backup_platform_android
import com.neoutils.finsight.resources.backup_platform_desktop
import com.neoutils.finsight.resources.backup_platform_ios
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.util.LocalDateFormats
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * The last thing between an approved file and an archive that is about to stop existing.
 *
 * It says which file, before it asks: what the file says about where it came from, and
 * how much it holds. That is the whole reason it is a sheet rather than a dialog with a
 * sentence in it — the operation is irreversible, and "restore a backup" is not a
 * question anyone can answer without knowing *which* backup.
 *
 * **What it says about undoing depends on what the app will actually do.** With a copy of
 * the current archive kept first, the restore stops being irreversible and the sheet says
 * so; with none kept, it is the one place in the app that says an operation cannot be
 * undone (`local-backup` spec). The replacement is total either way — what changed is that
 * there is something to come back to.
 *
 * @param isRestoring the flow rather than a value, because a modal is built once and
 * rendered by the manager that holds it: a boolean passed in would still read false while
 * the replacement ran.
 * @param keepsCopy the flow, for the same reason.
 * @param onDiscard called however the sheet was dismissed — the button, the scrim, the
 * swipe. The file this sheet is about is a copy nobody else owns, so leaving without an
 * answer is what removes it.
 */
class ConfirmRestoreModal(
    private val confirmation: RestoreConfirmation,
    private val isRestoring: StateFlow<Boolean>,
    private val keepsCopy: StateFlow<Boolean>,
    private val onConfirm: () -> Unit,
    private val onDiscard: () -> Unit,
) : ModalBottomSheet() {

    override fun onDismissed() {
        super.onDismissed()
        onDiscard()
    }

    /**
     * The sheet stays where it is while the replacement runs.
     *
     * There is nothing to call off — the swap is a single transaction — so a way out would
     * only take the spinner with it and leave the user watching the backup screen do nothing
     * in the middle of the one operation this app cannot undo. The same reasoning already
     * disables both buttons.
     */
    @Composable
    override fun isDismissible(): Boolean {
        val restoring by isRestoring.collectAsStateWithLifecycle()
        return !restoring
    }

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val modal = this@ConfirmRestoreModal
        val restoring by isRestoring.collectAsStateWithLifecycle()
        val reversible by keepsCopy.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.backup_confirm_title),
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.backup_confirm_message),
                    style = typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            FileIdentityCard(confirmation)

            if (reversible) ReversibleNotice() else IrreversibleNotice()

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { manager.dismiss(modal) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_restore_confirm_cancel"),
                    enabled = !restoring,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.backup_confirm_cancel),
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("backup_restore_confirm_action"),
                    // Both, and not only this one: the replacement is a single
                    // transaction, so there is nothing left to call off once it starts.
                    enabled = !restoring,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                ) {
                    if (restoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colorScheme.onError,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.backup_confirm_action),
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** Which file this is: where it came from, how much it holds, and when it was taken. */
@Composable
private fun FileIdentityCard(confirmation: RestoreConfirmation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OriginHeader(confirmation.origin)

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            ArchiveCountsGrid(confirmation.counts)

            // A file with no stamp keeps its counts and loses the two lines it has
            // nothing to say in. Three fields reading "unknown" would be noise dressed
            // as information.
            if (confirmation.origin != null) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))
                CapturedAt(confirmation.origin.createdAt)
            }
        }
    }
}

@Composable
private fun OriginHeader(origin: FileOrigin?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = origin.icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text = originLabel(origin),
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // A build that states no version of its own stamps none, and none is shown.
        if (origin != null && origin.appVersion.isNotBlank()) {
            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                color = colorScheme.primary.copy(alpha = 0.12f),
                contentColor = colorScheme.primary,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "v${origin.appVersion}",
                    style = typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * How much the file holds, by facade — the four the user recognises their own archive by.
 */
@Composable
private fun ArchiveCountsGrid(counts: ArchiveCounts) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountCell(stringResource(Res.string.backup_confirm_accounts), counts.accounts)
            CountCell(stringResource(Res.string.backup_confirm_transactions), counts.transactions)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountCell(stringResource(Res.string.backup_confirm_categories), counts.categories)
            CountCell(stringResource(Res.string.backup_confirm_credit_cards), counts.creditCards)
        }
    }
}

@Composable
private fun RowScope.CountCell(label: String, value: Long) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.toString(),
            style = typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
    }
}

@Composable
private fun CapturedAt(createdAt: Instant) {
    val dateFormats = LocalDateFormats.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "${dateFormats.formatInstantDate(createdAt)} · " +
                dateFormats.formatInstantTime(createdAt),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The only place the app says the operation cannot be undone, and it says it here because
 * this is where the answer is given — under the file it is about, over the button that
 * acts. The screen behind offers restore as an option and warns about nothing.
 */
@Composable
private fun IrreversibleNotice() {
    Surface(
        color = Warning.copy(alpha = 0.14f),
        contentColor = Warning,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.backup_confirm_irreversible),
                style = typography.bodySmall,
            )
        }
    }
}

/**
 * What is kept back, now that something is: the replacement is still total, and the way
 * back is a second restore rather than an undo — so this states where to find it and
 * claims nothing more.
 */
@Composable
private fun ReversibleNotice() {
    Surface(
        color = colorScheme.primary.copy(alpha = 0.12f),
        contentColor = colorScheme.primary,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_restore_confirm_reversible"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.backup_confirm_reversible),
                style = typography.bodySmall,
            )
        }
    }
}

/**
 * The name of the platform, when the file names one this build knows; the raw stamp when
 * it names one this build does not; and unknown origin when it names none at all. The
 * three cases are different: only the last is a file that said nothing.
 */
@Composable
private fun originLabel(origin: FileOrigin?): String = when (origin?.platform) {
    BackupPlatform.ANDROID -> stringResource(Res.string.backup_platform_android)
    BackupPlatform.DESKTOP -> stringResource(Res.string.backup_platform_desktop)
    BackupPlatform.IOS -> stringResource(Res.string.backup_platform_ios)
    null -> origin?.platformId ?: stringResource(Res.string.backup_confirm_origin_unknown)
}

private val FileOrigin?.icon: ImageVector
    get() = when (this?.platform) {
        BackupPlatform.ANDROID -> Icons.Outlined.Android
        BackupPlatform.DESKTOP -> Icons.Outlined.Computer
        BackupPlatform.IOS -> Icons.Outlined.PhoneIphone
        null -> Icons.Outlined.DeviceUnknown
    }
