package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.toMoneyFormat

data class InstallmentState(
    val count: Int,
    val total: Double,
) {
    val installment = total / count

    override fun toString(): String {
        if (count == 1) {
            return "${count}x"
        }

        return "${count}x de ${installment.toMoneyFormat()}"
    }
}

@Composable
fun InstallmentCounter(
    state: InstallmentState,
    onInstallmentsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minCount: Int = 1,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        }
    ) { state ->
        val canDecrease = state.count > minCount

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (canDecrease) {
                        onInstallmentsChange(state.count - 1)
                    }
                },
                enabled = canDecrease,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (canDecrease) colorScheme.primary else colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = state.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary
            )

            IconButton(
                onClick = { onInstallmentsChange(state.count + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
