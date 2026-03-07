@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.ui.extension.toUiText
import com.neoutils.finsight.ui.screen.creditCards.CreditCardUi
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.credit_card_ui_available_limit
import com.neoutils.finsight.resources.credit_card_ui_closes_on
import com.neoutils.finsight.resources.credit_card_ui_current_invoice
import com.neoutils.finsight.resources.credit_card_ui_due_on
import com.neoutils.finsight.resources.credit_card_ui_edit_invoice
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.LocalDateFormats
import com.neoutils.finsight.util.dayMonth
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreditCardUI(
    ui: CreditCardUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onEditInvoice: ((invoice: Invoice) -> Unit)? = null,
) {
    val formatter = LocalCurrencyFormatter.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val sharedModifier = sharedTransitionScope?.run {
        animatedVisibilityScope?.let {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(
                    key = "credit_card_${ui.creditCard.id}",
                ),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } ?: Modifier

    Card(
        modifier = modifier
            .then(sharedModifier)
            .then(
                if (onClick != null) {
                    Modifier.clip(shapes.large).clickable { onClick() }
                } else {
                    Modifier
                }
            ),
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
                        imageVector = AppIcon.fromKey(ui.creditCard.iconKey).icon,
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
                            text = stringResource(it.status.toUiText()),
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
                        text = stringResource(Res.string.credit_card_ui_current_invoice),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    ui.invoiceUi?.let { invoiceUi ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    if (invoiceUi.status.isEditable && onEditInvoice != null) {
                                        Modifier.clickable {
                                            onEditInvoice(invoiceUi.invoice)
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = formatter.format(invoiceUi.amount),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )

                            if (invoiceUi.status.isEditable && onEditInvoice != null) {
                                Icon(
                                    imageVector = Icons.Rounded.ModeEdit,
                                    contentDescription = stringResource(Res.string.credit_card_ui_edit_invoice),
                                    tint = colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } ?: Text(
                        text = "",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier
                            .width(100.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.surfaceVariant)
                    )
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
                        text = stringResource(Res.string.credit_card_ui_available_limit),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )

                    Row {
                        Text(
                            text = ui.invoiceUi?.availableLimit?.let { formatter.format(it) }
                                ?: formatter.format(ui.creditCard.limit),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            text = " / ${formatter.format(ui.creditCard.limit)}",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }

                ui.invoiceUi?.let { invoiceUi ->
                    val closesOnLabel = stringResource(Res.string.credit_card_ui_closes_on)
                    val dueOnLabel = stringResource(Res.string.credit_card_ui_due_on)
                    val dateInfo = when {
                        invoiceUi.status.isOpen -> closesOnLabel to invoiceUi.closingDate
                        invoiceUi.status.isClosed || invoiceUi.status.isRetroactive -> dueOnLabel to invoiceUi.dueDate
                        else -> null
                    }

                    dateInfo?.let { (dateLabel, date) ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = dateLabel,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dayMonth.format(date),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                        }
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
        }
    }
}
