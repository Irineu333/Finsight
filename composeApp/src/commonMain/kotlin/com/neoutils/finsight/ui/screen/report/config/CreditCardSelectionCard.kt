package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_ui_day
import com.neoutils.finsight.resources.credit_card_ui_due_on
import com.neoutils.finsight.resources.credit_card_ui_opens_on
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreditCardSelectionCard(
    card: CreditCard,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = AppIcon.fromKey(card.iconKey).icon,
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.credit_card_ui_opens_on),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.credit_card_ui_day, card.closingDay),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.credit_card_ui_due_on),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.credit_card_ui_day, card.dueDay),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
