@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.toMoneyFormat
import com.neoutils.finsight.ui.screen.dashboard.CreditCardUi
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_credit_card_advance
import com.neoutils.finsight.resources.dashboard_credit_card_available_limit
import com.neoutils.finsight.resources.dashboard_credit_card_close_invoice
import com.neoutils.finsight.resources.dashboard_credit_card_current_invoice
import com.neoutils.finsight.resources.dashboard_credit_card_pay_invoice
import org.jetbrains.compose.resources.stringResource

data class CreditCardUiConfig(
    val canEditAmount: Boolean = false,
    val canPayInvoice: Boolean,
    val canCloseInvoice: Boolean,
    val canAdvanceInvoice: Boolean,
) {
    companion object {
        fun from(creditCardUi: CreditCardUi): CreditCardUiConfig {

            val invoice = creditCardUi.invoiceUi

            return CreditCardUiConfig(
                canEditAmount = invoice != null,
                canCloseInvoice = invoice?.isClosable == true,
                canPayInvoice = invoice?.status == Invoice.Status.CLOSED,
                canAdvanceInvoice = invoice?.status == Invoice.Status.OPEN,
            )
        }
    }
}

@Composable
fun DashboardCreditCardUI(
    ui: CreditCardUi,
    config: CreditCardUiConfig,
    onClick: () -> Unit,
    onCloseInvoice: () -> Unit,
    onPayInvoice: () -> Unit,
    onAdvancePayment: () -> Unit,
    onEditAmount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val sharedModifier = sharedTransitionScope?.run {
        animatedVisibilityScope?.let {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "credit_card_${ui.creditCard.id}"
                ),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } ?: Modifier

    Card(
        modifier = modifier
            .then(sharedModifier)
            .clip(shapes.large)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainer
        ),
        shape = shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        text = ui.creditCard.name,
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                ui.invoiceUi?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = it.status.color.copy(alpha = 0.15f),
                            contentColor = it.status.color
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = it.status.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.dashboard_credit_card_current_invoice),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (config.canEditAmount) {
                                    Modifier.clickable { onEditAmount() }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        ui.invoiceUi?.let {
                            Text(
                                text = it.amount.toMoneyFormat(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        } ?: Text(
                            text = "",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .width(100.dp)
                                .background(colorScheme.surfaceVariant)
                        )

                        if (config.canEditAmount) {
                            Icon(
                                imageVector = Icons.Rounded.ModeEdit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.dashboard_credit_card_available_limit),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    Row {
                        Text(
                            text = ui.invoiceUi?.availableLimit?.toMoneyFormat()
                                ?: ui.creditCard.limit.toMoneyFormat(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            text = " / ${ui.creditCard.limit.toMoneyFormat()}",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }

            if (ui.invoiceUi != null && ui.invoiceUi.showProgress) {
                LinearProgressIndicator(
                    progress = { ui.invoiceUi.usagePercentage.toFloat() },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                    gapSize = (-4).dp,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (config.canCloseInvoice) {
                    OutlinedButton(
                        onClick = onCloseInvoice,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFFA726)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color(0xFFFFA726).copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.dashboard_credit_card_close_invoice),
                            fontSize = 14.sp
                        )
                    }
                }

                if (config.canPayInvoice) {
                    OutlinedButton(
                        onClick = onPayInvoice,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.primary
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = colorScheme.primary.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.dashboard_credit_card_pay_invoice),
                            fontSize = 14.sp
                        )
                    }
                }

                if (config.canAdvanceInvoice) {
                    OutlinedButton(
                        onClick = onAdvancePayment,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.primary
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = colorScheme.primary.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.dashboard_credit_card_advance),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
