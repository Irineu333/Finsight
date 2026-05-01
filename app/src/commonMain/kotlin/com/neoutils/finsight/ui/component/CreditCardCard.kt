@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.toUiText
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.ui.model.InvoiceUi
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.dayMonth
import org.jetbrains.compose.resources.stringResource

sealed class CreditCardCardVariant {

    data class Dashboard(
        val onClick: () -> Unit,
        val onCloseInvoice: () -> Unit,
        val onPayInvoice: () -> Unit,
        val onAdvancePayment: () -> Unit,
        val onEditAmount: () -> Unit,
    ) : CreditCardCardVariant()

    data class Listing(
        val onClick: (() -> Unit)? = null,
        val onEditInvoice: ((Invoice) -> Unit)? = null,
    ) : CreditCardCardVariant()

    data object Selection : CreditCardCardVariant()
}

@Composable
fun CreditCardCard(
    creditCard: CreditCard,
    variant: CreditCardCardVariant,
    modifier: Modifier = Modifier,
    invoiceUi: InvoiceUi? = null,
) {
    val formatter = LocalCurrencyFormatter.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val sharedModifier = sharedTransitionScope?.run {
        animatedVisibilityScope?.let {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "credit_card_${creditCard.id}"),
                animatedVisibilityScope = it,
            )
        }
    } ?: Modifier

    val onClick = when (variant) {
        is CreditCardCardVariant.Dashboard -> variant.onClick
        is CreditCardCardVariant.Listing -> variant.onClick
        is CreditCardCardVariant.Selection -> null
    }

    Card(
        modifier = modifier
            .then(sharedModifier)
            .then(
                if (onClick != null) Modifier.clip(shapes.large).clickable { onClick() }
                else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
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
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                if (variant !is CreditCardCardVariant.Selection) {
                    invoiceUi?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(it.status.colorValue).copy(alpha = 0.15f),
                                contentColor = Color(it.status.colorValue),
                            ),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = stringResource(it.status.toUiText()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (variant is CreditCardCardVariant.Selection) {
                Spacer(modifier = Modifier.height(20.dp))

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
                            text = stringResource(Res.string.credit_card_ui_day, creditCard.closingDay),
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
                            text = stringResource(Res.string.credit_card_ui_day, creditCard.dueDay),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                val onEdit: (() -> Unit)? = when (variant) {
                    is CreditCardCardVariant.Dashboard -> {
                        invoiceUi?.let { { variant.onEditAmount() } }
                    }

                    is CreditCardCardVariant.Listing -> {
                        invoiceUi
                            ?.takeIf { it.status.isEditable }
                            ?.let { inv ->
                                variant.onEditInvoice?.let { { it(inv.invoice) } }
                            }
                    }
                }

                Column {
                    Text(
                        text = stringResource(Res.string.credit_card_ui_current_invoice),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .then(if (onEdit != null) Modifier.clickable { onEdit() } else Modifier),
                    ) {
                        invoiceUi?.let {
                            Text(
                                text = formatter.format(it.amount),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                        } ?: Text(
                            text = "",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .width(100.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colorScheme.surfaceVariant),
                        )

                        if (onEdit != null) {
                            Icon(
                                imageVector = Icons.Rounded.ModeEdit,
                                contentDescription = stringResource(Res.string.credit_card_ui_edit_invoice),
                                tint = colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.credit_card_ui_available_limit),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Row {
                            Text(
                                text = invoiceUi?.availableLimit?.let { formatter.format(it) }
                                    ?: formatter.format(creditCard.limit),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Text(
                                text = " / ${formatter.format(creditCard.limit)}",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }

                    if (variant is CreditCardCardVariant.Listing) {
                        invoiceUi?.let { invoiceUi ->
                            val closesOnLabel = stringResource(Res.string.credit_card_ui_closes_on)
                            val dueOnLabel = stringResource(Res.string.credit_card_ui_due_on)
                            val dateInfo = when {
                                invoiceUi.status.isOpen -> closesOnLabel to invoiceUi.closingDate
                                invoiceUi.status.isClosed || invoiceUi.status.isRetroactive -> dueOnLabel to invoiceUi.dueDate
                                else -> null
                            }
                            dateInfo?.let { (label, date) ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = dayMonth.format(date),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                if (invoiceUi?.showProgress == true) {
                    LinearProgressIndicator(
                        progress = { invoiceUi.usagePercentage.toFloat() },
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

                if (variant is CreditCardCardVariant.Dashboard) {
                    val canCloseInvoice = invoiceUi?.isClosable == true
                    val canPayInvoice = invoiceUi?.status == Invoice.Status.CLOSED
                    val canAdvanceInvoice = invoiceUi?.status == Invoice.Status.OPEN

                    if (canCloseInvoice || canPayInvoice || canAdvanceInvoice) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (canCloseInvoice) {
                                OutlinedButton(
                                    onClick = variant.onCloseInvoice,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFFFA726),
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = Color(0xFFFFA726).copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.dashboard_credit_card_close_invoice),
                                        fontSize = 14.sp,
                                    )
                                }
                            }

                            if (canPayInvoice) {
                                OutlinedButton(
                                    onClick = variant.onPayInvoice,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colorScheme.primary,
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = colorScheme.primary.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.dashboard_credit_card_pay_invoice),
                                        fontSize = 14.sp,
                                    )
                                }
                            }

                            if (canAdvanceInvoice) {
                                OutlinedButton(
                                    onClick = variant.onAdvancePayment,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colorScheme.primary,
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = colorScheme.primary.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.dashboard_credit_card_advance),
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}