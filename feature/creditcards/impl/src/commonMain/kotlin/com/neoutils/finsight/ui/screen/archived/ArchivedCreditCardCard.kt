package com.neoutils.finsight.ui.screen.archived

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_archived
import com.neoutils.finsight.resources.credit_card_form_limit_label
import com.neoutils.finsight.resources.credit_card_ui_closes_on
import com.neoutils.finsight.resources.credit_card_ui_day
import com.neoutils.finsight.resources.credit_card_ui_due_on
import com.neoutils.finsight.ui.model.ArchivedCreditCardUi
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArchivedCreditCardCard(
    creditCard: ArchivedCreditCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current

    Card(
        modifier = modifier.clip(shapes.large).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = AppIcon.fromKey(creditCard.iconKey).icon,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = creditCard.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                    )
                }

                // The muted card alone cannot carry "archived" — an explicit icon +
                // label states it in the clear (parity with CategoryCard).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.credit_card_archived),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Attribute(
                label = stringResource(Res.string.credit_card_form_limit_label),
                value = formatter.format(creditCard.limit),
                valueSize = 20.sp,
            )

            Spacer(Modifier.height(16.dp))

            // Two attributes pinned to the edges (Start/End) stay symmetric — a third
            // one in a SpaceBetween row would float to the centre and read as broken.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Attribute(
                    label = stringResource(Res.string.credit_card_ui_closes_on),
                    value = stringResource(Res.string.credit_card_ui_day, creditCard.closingDay),
                )
                Attribute(
                    label = stringResource(Res.string.credit_card_ui_due_on),
                    value = stringResource(Res.string.credit_card_ui_day, creditCard.dueDay),
                    alignment = Alignment.End,
                )
            }
        }
    }
}

@Composable
private fun Attribute(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start,
    valueSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    Column(horizontalAlignment = alignment) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = valueSize,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
    }
}
