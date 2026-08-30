@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.vaultSettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.domain.vault.copiesKept
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_settings_interval_hint
import com.neoutils.finsight.resources.backup_settings_interval_title
import com.neoutils.finsight.resources.backup_settings_outcome
import com.neoutils.finsight.resources.backup_settings_outcome_unknown
import com.neoutils.finsight.resources.backup_settings_periodic_subtitle
import com.neoutils.finsight.resources.backup_settings_periodic_title
import com.neoutils.finsight.resources.backup_settings_preventive_subtitle
import com.neoutils.finsight.resources.backup_settings_preventive_title
import com.neoutils.finsight.resources.backup_settings_retention_all
import com.neoutils.finsight.resources.backup_settings_retention_fixed
import com.neoutils.finsight.resources.backup_settings_retention_title
import com.neoutils.finsight.resources.backup_settings_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.BackupAction
import com.neoutils.finsight.ui.screen.backup.VaultCopies
import com.neoutils.finsight.ui.screen.backup.intervalLabel
import com.neoutils.finsight.ui.screen.backup.retentionLabel
import com.neoutils.finsight.ui.screen.backup.sizeLabel
import com.neoutils.finsight.ui.theme.Warning
import com.neoutils.finsight.ui.theme.finsightSwitchColors
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * What the user may set about the vault: which occasions produce a copy, how long a copy is
 * allowed to be the newest one, and how many are kept.
 *
 * **There is no fourth thing to set, and that is by construction.** Which actions are worth
 * a copy is decided in the domain by class, so this sheet decides *whether* the rule
 * applies and never *which* rule it is (design D7) — a control for it would be a second
 * owner of the classification.
 *
 * **The preventive trigger is at the top, as a switch.** It is the one that makes a
 * deletion reversible, which is the most consequential of the three, and burying it under
 * a list of numbers would rank it by how easy it is to render.
 *
 * **The interval says "check every", never "make a copy every".** No supported platform
 * lets an app work on its own in the background, so a promise of periodicity is a sentence
 * the app cannot keep (design D5); the line under the choice says why in the same words a
 * person would.
 *
 * **The combination says what it produces.** "Three days" and "ten copies" are two abstract
 * numbers; "about a month of history, around 42 MB" is a decision — and the size is the
 * real one, averaged over the copies already taken, rather than a guess.
 *
 * Nothing here is saved, because nothing here is edited: every control writes the
 * preference as it is touched, which is what every other preference in this app does. There
 * are therefore no buttons.
 *
 * @param state the flow, because a modal is built once and rendered by the manager that
 * holds it — a value passed in would still describe the vault as it was when the sheet
 * opened.
 */
class VaultSettingsModal(
    private val state: StateFlow<VaultState>,
    private val copies: StateFlow<VaultCopies>,
    private val onAction: (BackupAction) -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val vault by state.collectAsStateWithLifecycle()
        val stored by copies.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_vault_settings"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.backup_settings_title),
                style = typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            SwitchRow(
                title = stringResource(Res.string.backup_settings_preventive_title),
                subtitle = stringResource(Res.string.backup_settings_preventive_subtitle),
                checked = vault.isPreventiveOn,
                tag = "backup_preventive_switch",
                onCheckedChange = { onAction(BackupAction.SetPreventiveOn(it)) },
            )

            SwitchRow(
                title = stringResource(Res.string.backup_settings_periodic_title),
                subtitle = stringResource(Res.string.backup_settings_periodic_subtitle),
                checked = vault.isPeriodicOn,
                tag = "backup_periodic_switch",
                onCheckedChange = { onAction(BackupAction.SetPeriodicOn(it)) },
            )

            Choice(
                title = stringResource(Res.string.backup_settings_interval_title),
                hint = stringResource(Res.string.backup_settings_interval_hint),
            ) {
                val selected = VaultInterval.nearest(vault.interval)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VaultInterval.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == selected,
                            onClick = { onAction(BackupAction.SetInterval(option)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = VaultInterval.entries.size,
                            ),
                            icon = {},
                            modifier = Modifier.testTag("backup_interval_${option.name}"),
                        ) {
                            Text(text = intervalLabel(option))
                        }
                    }
                }
            }

            Retention(vault = vault, onAction = onAction)

            Outcome(vault = vault, copies = stored)
        }
    }
}

/**
 * How many copies are kept — a choice where it is the user's folder and their space, and a
 * statement where it is not.
 *
 * The app's own storage keeps a fixed, small number and offers no control over it
 * (design D10): these are files nobody sees and nobody administers, so a setting for them
 * would be configuration without a purpose — and a control that wrote a preference the
 * sweep does not read would be worse than none.
 */
@Composable
private fun Retention(vault: VaultState, onAction: (BackupAction) -> Unit) {
    when (vault.destination) {
        VaultDestination.APP_STORAGE -> Notice(
            text = stringResource(
                Res.string.backup_settings_retention_fixed,
                vault.copiesKept() ?: 0,
            ),
            tone = colorScheme.onSurfaceVariant,
            container = colorScheme.surfaceContainer,
            icon = Icons.Outlined.Info,
            tag = "backup_retention_fixed",
        )

        VaultDestination.USER_FOLDER -> Choice(
            title = stringResource(Res.string.backup_settings_retention_title),
            hint = null,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BackupRetention.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == vault.retention,
                        onClick = { onAction(BackupAction.SetRetention(option)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = BackupRetention.entries.size,
                        ),
                        icon = {},
                        modifier = Modifier.testTag("backup_retention_${option.name}"),
                    ) {
                        Text(text = retentionLabel(option))
                    }
                }
            }
        }
    }
}

/**
 * What the interval and the limit produce together: how far back the history reaches, and
 * how much room it takes.
 *
 * The room is measured, not estimated — it is the average of the copies already written —
 * so a vault that has never captured says how far back it will reach and stays quiet about
 * the size rather than inventing one.
 *
 * Keeping everything is not prevented and is not silent: it turns retention into something
 * the user switched off rather than something they put up with, and somebody who chooses it
 * is owed the rate the folder grows at.
 */
@Composable
private fun Outcome(vault: VaultState, copies: VaultCopies) {
    val kept = vault.copiesKept()
    val days = VaultInterval.nearest(vault.interval).duration.inWholeDays.toInt()
    val average = if (copies.count > 0) copies.totalBytes / copies.count else 0L

    if (kept == null) {
        Notice(
            text = stringResource(
                Res.string.backup_settings_retention_all,
                sizeLabel(average * (DAYS_PER_MONTH / days.coerceAtLeast(1))),
            ),
            tone = Warning,
            container = Warning.copy(alpha = 0.14f),
            icon = Icons.Outlined.WarningAmber,
            tag = "backup_retention_all_warning",
        )
        return
    }

    Notice(
        text = if (average > 0) {
            stringResource(
                Res.string.backup_settings_outcome,
                days * kept,
                sizeLabel(average * kept),
            )
        } else {
            stringResource(Res.string.backup_settings_outcome_unknown, days * kept)
        },
        tone = colorScheme.onSurfaceVariant,
        container = colorScheme.surfaceContainer,
        icon = Icons.Outlined.Info,
        tag = "backup_settings_outcome",
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = finsightSwitchColors(),
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun Choice(
    title: String,
    hint: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
        )
        content()
        if (hint != null) {
            Text(
                text = hint,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Notice(
    text: String,
    tone: Color,
    container: Color,
    icon: ImageVector,
    tag: String,
) {
    Surface(
        color = container,
        contentColor = tone,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text = text, style = typography.bodySmall)
        }
    }
}

/** Long enough to be a month for a sentence about how fast a folder grows. */
private const val DAYS_PER_MONTH = 30
