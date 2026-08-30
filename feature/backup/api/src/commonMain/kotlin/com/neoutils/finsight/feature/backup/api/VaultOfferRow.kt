package com.neoutils.finsight.feature.backup.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_offer_subtitle
import com.neoutils.finsight.resources.backup_confirm_offer_title
import com.neoutils.finsight.util.stringUiText
import org.jetbrains.compose.resources.stringResource

/**
 * The vault, offered where the risk it covers is: one box, already taken, and a sentence
 * that says the whole of what accepting does.
 *
 * It sits inside the confirmation rather than over it. The sheet underneath is what states
 * what is about to go; this adds the one thing that can still be changed about it, and the
 * button that acts is still the confirmation's own.
 *
 * Saying that it also copies every few days from now on is what separates an offer from a
 * trick — accepting turns the vault on, not this one copy — and it is why the sentence
 * names the interval and where to switch it off.
 *
 * **A confirmation with nothing to offer renders nothing**, including whatever spacing this
 * was given: whether there is still an offer to make is [VaultOfferState]'s answer, and a
 * sheet that asked it a second time would be a second gate.
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
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        onClick = { state.setAccepted(!isAccepted) },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_vault_offer"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isAccepted,
                onCheckedChange = state::setAccepted,
                enabled = enabled,
                modifier = Modifier.testTag("backup_vault_offer_check"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.backup_confirm_offer_title),
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        Res.string.backup_confirm_offer_subtitle,
                        stringUiText(terms.intervalLabel),
                    ),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
