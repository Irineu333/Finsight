@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.vaultSettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.VaultInterval
import com.neoutils.finsight.domain.vault.VaultState
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_settings_interval_hint
import com.neoutils.finsight.resources.backup_settings_interval_title
import com.neoutils.finsight.resources.backup_settings_outcome
import com.neoutils.finsight.resources.backup_settings_outcome_size
import com.neoutils.finsight.resources.backup_settings_outcome_unknown
import com.neoutils.finsight.resources.backup_settings_periodic_subtitle
import com.neoutils.finsight.resources.backup_settings_periodic_title
import com.neoutils.finsight.resources.backup_settings_preventive_subtitle
import com.neoutils.finsight.resources.backup_settings_preventive_title
import com.neoutils.finsight.resources.backup_settings_retention_all
import com.neoutils.finsight.resources.backup_settings_retention_all_by_hand
import com.neoutils.finsight.resources.backup_settings_retention_all_hint
import com.neoutils.finsight.resources.backup_settings_retention_title
import com.neoutils.finsight.resources.backup_settings_title
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.BackupAction
import com.neoutils.finsight.ui.screen.backup.GroupGap
import com.neoutils.finsight.ui.screen.backup.RowGap
import com.neoutils.finsight.ui.screen.backup.TileShape
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
 * **The wait is shown only while the trigger that reads it is on.** The periodic trigger's
 * question about time is what consults the interval (`VaultPeriodicBackup`), so with that
 * switch off nothing checks anything, and a row headed "check every" would name a rhythm the
 * app has stopped keeping. It arrives and leaves with the switch rather than greying out: a
 * disabled control still asserts that the setting matters, and this one stops mattering
 * entirely.
 *
 * **How many copies are kept is asked in every state.** The sweep hangs off any capture that
 * landed, whichever trigger produced it (`BackupVault`), so the limit is in force with the
 * periodic trigger off, with the preventive trigger off, and with both — it is the one
 * setting on this sheet that governs copies the vault has already written. That includes the
 * choice to remove nothing, which is stated in amber rather than prevented, and the amber
 * belongs to the choice rather than to any trigger.
 *
 * **What the settings produce is said as far as it can be sourced.** "Three days" and "ten
 * copies" are two abstract numbers; "about a month of history, around 42 MB" is a decision —
 * and the size is the real one, averaged over the copies already taken, rather than a guess.
 * The readings have different sources and are worked out apart (`VaultOutcome`): the room a
 * limit takes is that limit and that average, so it holds wherever the limit does, while a
 * span of history and a rate per month need a wait as well and go quiet without one. A
 * sentence half of which has lost its source is shortened, never dropped.
 *
 * **It is laid out on the beat both backup screens use** (`BackupRows`): the same corner,
 * the same gap between the rows of one group, and the wider gap only where a group opens.
 * Three groups stand here — the two triggers, the wait, and the limit with what it produces
 * — of which only the wait comes and goes, growing and collapsing rather than appearing
 * under the reader's thumb. What the limit produces sits inside the limit's own group,
 * against the picker that decides it, because it is that choice read back rather than a
 * fourth subject.
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
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text(
                text = stringResource(Res.string.backup_settings_title),
                style = typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            SwitchTile(
                title = stringResource(Res.string.backup_settings_preventive_title),
                subtitle = stringResource(Res.string.backup_settings_preventive_subtitle),
                checked = vault.isPreventiveOn,
                tag = "backup_preventive_switch",
                onCheckedChange = { onAction(BackupAction.SetPreventiveOn(it)) },
                modifier = Modifier.padding(top = GroupGap - RowGap),
            )

            SwitchTile(
                title = stringResource(Res.string.backup_settings_periodic_title),
                subtitle = stringResource(Res.string.backup_settings_periodic_subtitle),
                checked = vault.isPeriodicOn,
                tag = "backup_periodic_switch",
                onCheckedChange = { onAction(BackupAction.SetPeriodicOn(it)) },
            )

            AnimatedVisibility(visible = vault.isPeriodicOn) {
                SegmentedChoice(
                    title = stringResource(Res.string.backup_settings_interval_title),
                    hint = stringResource(Res.string.backup_settings_interval_hint),
                    options = VaultInterval.entries,
                    selected = VaultInterval.nearest(vault.interval),
                    label = { intervalLabel(it) },
                    tag = { "backup_interval_${it.name}" },
                    onSelect = { onAction(BackupAction.SetInterval(it)) },
                    modifier = Modifier.padding(top = GroupGap - RowGap),
                )
            }

            SegmentedChoice(
                title = stringResource(Res.string.backup_settings_retention_title),
                hint = null,
                options = BackupRetention.entries,
                selected = vault.retention,
                label = { retentionLabel(it) },
                tag = { "backup_retention_${it.name}" },
                onSelect = { onAction(BackupAction.SetRetention(it)) },
                modifier = Modifier.padding(top = GroupGap - RowGap),
            )

            Outcome(vault = vault, copies = stored)
        }
    }
}

/**
 * What the limit produces, and — while a wait is in force — how far back that reaches.
 *
 * The room is measured, not estimated: it is the average of the copies already written, so
 * a vault that has never captured says so rather than inventing a size. The span and the
 * rate per month are the readings that need a wait as well, and they are the ones that go
 * quiet when the periodic trigger is off; what is left is stated on its own line rather than
 * padded with a second sentence that has nothing behind it.
 *
 * Keeping everything is not prevented and is not silent: it turns retention into something
 * the user switched off rather than something they put up with, and it says so wherever the
 * choice is made, because the limit is read behind every capture whatever set it off.
 */
@Composable
private fun Outcome(vault: VaultState, copies: VaultCopies, modifier: Modifier = Modifier) {
    when (val outcome = vaultOutcome(vault, copies)) {
        is VaultOutcome.KeepsEverything -> OutcomeBox(
            value = stringResource(Res.string.backup_settings_retention_all),
            hint = outcome.perMonthBytes?.let {
                stringResource(Res.string.backup_settings_retention_all_hint, sizeLabel(it))
            } ?: stringResource(Res.string.backup_settings_retention_all_by_hand),
            tone = Warning,
            container = Warning.copy(alpha = 0.14f),
            tag = "backup_retention_all_warning",
            modifier = modifier,
        )

        is VaultOutcome.Keeps -> {
            val room = outcome.eachBytes?.let {
                stringResource(
                    Res.string.backup_settings_outcome_size,
                    outcome.copies,
                    sizeLabel(it),
                    sizeLabel(it * outcome.copies),
                )
            } ?: stringResource(Res.string.backup_settings_outcome_unknown)

            OutcomeBox(
                value = outcome.historyDays
                    ?.let { stringResource(Res.string.backup_settings_outcome, it) }
                    ?: room,
                hint = outcome.historyDays?.let { room },
                tone = colorScheme.onSurface,
                container = colorScheme.background,
                tag = "backup_settings_outcome",
                modifier = modifier,
            )
        }
    }
}

/**
 * One trigger, as a card of the same make as every other card in this feature: the corner
 * the lists use, the padding the lists use, and a ground a step below the sheet.
 *
 * The ground is `background` and not `surfaceContainer`: the two names are one colour in
 * this theme, so a card painted the second inside a sheet painted `surface` is a card
 * nobody can see.
 *
 * **The tile is the switch, and not a switch with words beside it.** The whole row is
 * `toggleable` with [Role.Switch], and the [Switch] takes no callback of its own — so there
 * is one target where the eye sees one control, one ripple over the card rather than a spot
 * in its corner, and one thing announced to a screen reader. Giving the switch a callback
 * as well would put a second target inside the first and state the same control twice.
 *
 * The tag is on the row for the same reason: it names the control, and the control is the
 * row. A driver taps what it finds under that id and the setting flips.
 */
@Composable
private fun SwitchTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colorScheme.background,
        shape = TileShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .testTag(tag)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = typography.titleMedium,
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
                onCheckedChange = null,
                colors = finsightSwitchColors(),
            )
        }
    }
}

/**
 * A label, a row of short mutually exclusive values, and — where the choice needs one — the
 * line that says what it means.
 *
 * The two choices this sheet puts are the same control over different values, so they are
 * one composable: a second copy of it would be a second place the selected value could stop
 * being the accent.
 *
 * The choice sits directly on the sheet rather than in a card. It is not a statement the
 * sheet makes, it is the sheet asking, and the row's own border already bounds it.
 */
@Composable
private fun <T> SegmentedChoice(
    title: String,
    hint: String?,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = colorScheme.primary.copy(alpha = 0.2f),
        activeContentColor = colorScheme.primary,
        activeBorderColor = colorScheme.primary,
        inactiveContainerColor = colorScheme.surfaceContainer,
        inactiveContentColor = colorScheme.onSurfaceVariant,
        inactiveBorderColor = colorScheme.outline,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = typography.titleSmall,
            color = colorScheme.onSurface,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                    colors = colors,
                    icon = {},
                    modifier = Modifier.testTag(tag(option)),
                ) {
                    Text(text = label(option))
                }
            }
        }

        if (hint != null) {
            Text(
                text = hint,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A statement the sheet makes about itself: the reading on one line and, where there is a
 * second thing to say about it, why it comes out that way on the next.
 *
 * [hint] is null where the settings in force source one reading and not two. An empty line
 * would be the box asserting that something is missing, when what is there is simply the
 * whole of what can be said.
 *
 * The ground is `background` and not `surfaceContainer` for the reason [SwitchTile]'s is.
 * The warning tone paints its own tint instead, and needs no such step: the amber *is* the
 * signal, which is why there is no icon beside it — a second mark for the same thing.
 */
@Composable
private fun OutcomeBox(
    value: String,
    hint: String?,
    tone: Color,
    container: Color,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = container,
        shape = TileShape,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = value,
                style = typography.titleSmall,
                color = tone,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
