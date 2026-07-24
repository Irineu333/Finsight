package com.neoutils.finsight.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The outlined "act on this facade" button — retire, edit, transfer, unarchive.
 *
 * It was written out four times (accounts: retire/edit/transfer, category:
 * retire/edit) with the same shape, border and icon size, which is exactly how
 * accounts and cards drifted to different presentations of the same action. One
 * owner: [contentColor] tints border, icon and label together, so a caller picks a
 * colour, not a whole style.
 */
@Composable
fun OutlinedActionButton(
    label: String,
    icon: ImageVector,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) contentColor else colorScheme.onSurface.copy(alpha = 0.12f),
        ),
        contentPadding = PaddingValues(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = fontWeight,
        )
    }
}
