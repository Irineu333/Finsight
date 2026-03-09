package com.neoutils.finsight.ui.screen.report.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
            contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.width(104.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = AppIcon.fromKey(account.iconKey).icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = account.name,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
