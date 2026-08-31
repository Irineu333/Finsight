package com.neoutils.finsight.feature.backup.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_offer_reminder
import com.neoutils.finsight.resources.backup_confirm_offer_subtitle
import com.neoutils.finsight.resources.backup_confirm_offer_title
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

/**
 * The vault, offered where the risk it covers is: one box and a sentence that says the
 * whole of what accepting does.
 *
 * It sits inside the confirmation rather than over it. The sheet underneath is what states
 * what is about to go; this adds the one thing that can still be changed about it, and the
 * button that acts is still the confirmation's own.
 *
 * Saying that it also copies every few days from now on is what separates an offer from a
 * trick — accepting turns the vault on, not this one copy — and it is why the sentence
 * names the interval and where to switch it off.
 *
 * **It is a card of the same make as every other in this feature**, and it is a step
 * recessed from the sheet rather than the colour of it: `surface` and `surfaceContainer` are
 * one colour in this theme, so a box painted the second inside a sheet painted the first is
 * an invisible box. The recessed ground is `background`, which is what the drawing uses.
 *
 * **A vault that is already on renders nothing**, including whatever spacing this was
 * given: whether there is anything left to offer is [VaultOfferState]'s answer, and a sheet
 * that asked it a second time would be a second gate.
 *
 * **The card is the box.** The whole row is `toggleable` with [Role.Checkbox] and the
 * [Checkbox] takes no callback of its own, so there is one target where the eye sees one
 * control and one thing announced to a screen reader — and the tag names that row, because
 * that row is what a tap has to land on.
 */
@Composable
fun VaultOfferRow(
    state: VaultOfferState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val terms = state.terms ?: return
    val isAccepted by state.isAccepted.collectAsStateWithLifecycle()

    Surface(
        color = colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                // The card is the target, so the row that carries it is as wide as the
                // card and not as wide as its words.
                .fillMaxWidth()
                .toggleable(
                    value = isAccepted,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = state::setAccepted,
                )
                .testTag("backup_vault_offer")
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isAccepted,
                onCheckedChange = null,
                enabled = enabled,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.backup_confirm_offer_title),
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                // The offer somebody has already turned down says so, and still states the
                // price: it is the same offer, put again without insisting.
                Text(
                    text = stringResource(
                        if (terms.wasDeclined) {
                            Res.string.backup_confirm_offer_reminder
                        } else {
                            Res.string.backup_confirm_offer_subtitle
                        },
                        stringUiText(terms.intervalLabel),
                    ),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
