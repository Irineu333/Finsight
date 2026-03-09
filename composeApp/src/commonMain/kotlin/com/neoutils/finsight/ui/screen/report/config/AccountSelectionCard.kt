package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.util.AppIcon

@Composable
fun AccountSelectionCard(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainer
    val contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val iconColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
    val nameColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .width(156.dp)
            .height(88.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcon.fromKey(account.iconKey).icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )

                if (selected) {
                    Surface(
                        color = colorScheme.primary.copy(alpha = 0.18f),
                        contentColor = colorScheme.primary,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(12.dp),
                        )
                    }
                }
            }

            Text(
                text = account.name,
                style = MaterialTheme.typography.labelMedium,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
