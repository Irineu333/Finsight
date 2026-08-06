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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.installment_counter_label
import com.neoutils.finsight.resources.installment_counter_single
import org.jetbrains.compose.resources.stringResource

data class InstallmentState(
    val count: Int,
    val total: Double,
) {
    /**
     * What one instalment is worth, for the label alone.
     *
     * It is a plain division on purpose: this says "roughly this much a month" while
     * the user is still choosing. What is actually written is
     * `AddInstallmentUseCase`'s business — it splits in cents and gives the remainder
     * to the last instalment, so an amount that does not divide is off by a cent here
     * and correct in the ledger.
     */
    val installment = total / count
}

@Composable
fun InstallmentCounter(
    state: InstallmentState,
    onInstallmentsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minCount: Int = 1,
) {
    val formatter = LocalCurrencyFormatter.current

    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        modifier = modifier,
    ) { state ->
        val canDecrease = state.count > minCount

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (canDecrease) {
                        onInstallmentsChange(state.count - 1)
                    }
                },
                enabled = canDecrease,
                modifier = Modifier.testTag("installment_counter_decrease"),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (canDecrease) colorScheme.primary else colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = if (state.count == 1) {
                    stringResource(Res.string.installment_counter_single, state.count)
                } else {
                    stringResource(
                        Res.string.installment_counter_label,
                        state.count,
                        formatter.format(state.installment),
                    )
                },
                modifier = Modifier.testTag("installment_counter_label"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary
            )

            IconButton(
                onClick = {
                    onInstallmentsChange(state.count + 1)
                },
                modifier = Modifier.testTag("installment_counter_increase"),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
