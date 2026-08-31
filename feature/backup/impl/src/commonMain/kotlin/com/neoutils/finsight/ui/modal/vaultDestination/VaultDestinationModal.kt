package com.neoutils.finsight.ui.modal.vaultDestination

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_coverage_app
import com.neoutils.finsight.resources.backup_coverage_folder
import com.neoutils.finsight.resources.backup_destination_app
import com.neoutils.finsight.resources.backup_destination_folder
import com.neoutils.finsight.resources.backup_destination_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.screen.backup.GroupGap
import com.neoutils.finsight.ui.screen.backup.RowGap
import com.neoutils.finsight.ui.screen.backup.TileShape
import org.jetbrains.compose.resources.stringResource

/**
 * Where the copies are kept: the app's own storage, or a folder the person points at.
 *
 * **The two rungs are put side by side with what each one does not cover**, which is the
 * only honest way to ask this: they differ in exactly one thing a person cares about — what
 * survives the device — and the sentences that say so are the ones the screen behind
 * already shows for whichever is in force (design D3, design D16). No platform is named in
 * either, and no provider is judged in either; the app takes the folder somebody picks.
 *
 * **Choosing the folder is a picker, and the sheet gets out of its way.** Both choices close
 * this sheet: one writes a preference, the other raises the platform's folder chooser over
 * the screen. A picker somebody closes leaves everything exactly as it was, which is why
 * nothing here is drawn as pending.
 *
 * **Neither direction removes anything.** Moving to a folder leaves the copies inside the
 * app where they are; moving back leaves the copies in the folder where they are, and the
 * folder itself remembered, so choosing it again finds them (design D4). Whether any of them
 * are *also* written into the destination being moved to is a question put afterwards, over
 * this sheet's own dismissal, and answered by the person — carrying copies and never moving
 * them is design D13, and asking first is design D12's rule about somebody else's backups.
 *
 * @param selected the rung in force when the sheet was opened, which is all it needs: every
 * choice on it closes it.
 */
class VaultDestinationModal(
    private val selected: VaultDestination,
    private val onChooseFolder: () -> Unit,
    private val onKeepInsideApp: () -> Unit,
) : ModalBottomSheet() {

    @Composable
    override fun ColumnScope.BottomSheetContent() {
        val manager = LocalModalManager.current
        val modal = this@VaultDestinationModal

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("backup_vault_destination"),
        ) {
            Text(
                text = stringResource(Res.string.backup_destination_title),
                style = typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            DestinationTile(
                title = stringResource(Res.string.backup_destination_app),
                coverage = stringResource(Res.string.backup_coverage_app),
                isSelected = selected == VaultDestination.APP_STORAGE,
                tag = "backup_destination_app_option",
                onSelect = {
                    manager.dismiss(modal)
                    onKeepInsideApp()
                },
                modifier = Modifier.padding(top = GroupGap),
            )

            DestinationTile(
                title = stringResource(Res.string.backup_destination_folder),
                coverage = stringResource(Res.string.backup_coverage_folder),
                isSelected = selected == VaultDestination.USER_FOLDER,
                tag = "backup_destination_folder_option",
                // Offered even while it is the one in force: pointing at a folder again is
                // the same act as pointing at one for the first time, and it is how a link
                // that fell is repaired and how an archive is found after a reinstall
                // (design D4).
                onSelect = {
                    manager.dismiss(modal)
                    onChooseFolder()
                },
                modifier = Modifier.padding(top = RowGap),
            )
        }
    }
}

/**
 * One rung, as a card of the same make as the switches on the settings sheet: the corner the
 * lists use, the padding the lists use, and a ground a step below the sheet.
 *
 * The whole card is `selectable` with [Role.RadioButton], so there is one target where the
 * eye sees one choice and one thing announced to a screen reader. The glyph carries the
 * selection and takes no callback of its own.
 */
@Composable
private fun DestinationTile(
    title: String,
    coverage: String,
    isSelected: Boolean,
    tag: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = colorScheme.background,
        shape = TileShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
                .testTag(tag)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isSelected) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = coverage,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
