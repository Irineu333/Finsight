package com.neoutils.finsight.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ModeEdit
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.ui.theme.InvoicePayment
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.accounts_advance_payments
import com.neoutils.finsight.resources.accounts_adjustments
import com.neoutils.finsight.resources.accounts_balance
import com.neoutils.finsight.resources.accounts_default
import com.neoutils.finsight.resources.accounts_expenses
import com.neoutils.finsight.resources.accounts_income
import com.neoutils.finsight.resources.accounts_opening_balance
import com.neoutils.finsight.resources.accounts_invoices
import com.neoutils.finsight.resources.accounts_yield
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource

sealed class AccountCardVariant {

    data class Dashboard(
        val balance: Double,
        val onClick: () -> Unit,
    ) : AccountCardVariant()

    data class Selection(
        val selected: Boolean,
        val onClick: () -> Unit,
    ) : AccountCardVariant()

    data class Detail(
        val accountUi: AccountUi,
        val onEditBalance: () -> Unit,
        val onEditOpeningBalance: () -> Unit,
        val onLaunchYield: () -> Unit,
    ) : AccountCardVariant()
}

@Composable
fun AccountCard(
    iconKey: String,
    name: String,
    isDefault: Boolean,
    variant: AccountCardVariant,
    modifier: Modifier = Modifier,
) {
    val isDetail = variant is AccountCardVariant.Detail
    val isSelection = variant is AccountCardVariant.Selection

    val selected = isSelection && (variant as AccountCardVariant.Selection).selected

    val onClick = when (variant) {
        is AccountCardVariant.Dashboard -> variant.onClick
        is AccountCardVariant.Selection -> variant.onClick
        is AccountCardVariant.Detail -> null
    }

    val sizeModifier = when {
        isDetail -> Modifier
        isSelection -> Modifier.width(156.dp).height(88.dp)
        else -> Modifier.width(156.dp).height(112.dp)
    }

    Card(
        modifier = modifier
            .then(sizeModifier)
            .then(
                if (onClick != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() }
                else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        border = if (selected) BorderStroke(2.dp, colorScheme.primary) else null,
    ) {
        if (isDetail) {
            DetailContent(
                iconKey = iconKey,
                name = name,
                isDefault = isDefault,
                variant = variant as AccountCardVariant.Detail,
            )
        } else {
            CompactContent(
                iconKey = iconKey,
                name = name,
                isDefault = isDefault,
                variant = variant,
            )
        }
    }
}

@Composable
private fun DetailContent(
    iconKey: String,
    name: String,
    isDefault: Boolean,
    variant: AccountCardVariant.Detail,
) {
    val accountUi = variant.accountUi

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcon.fromKey(iconKey).icon,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isDefault) {
                Surface(
                    color = colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = colorScheme.primary,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.accounts_default),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_opening_balance),
            amount = accountUi.openingBalance,
            color = colorScheme.onSurface,
            onEditClick = variant.onEditOpeningBalance,
        )

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_income),
            amount = accountUi.income,
            color = Income,
        )

        // The declaration puts the line on screen at zero — a freshly declared account
        // has yielded nothing yet, and a line that appeared only once it had would
        // leave the first launch nowhere to be tapped. A yield already recorded keeps
        // it there whatever the account declares now, because `income` no longer holds
        // that money and the column has to keep adding up.
        //
        // Only the declaration makes it *actionable*, though: an account that no longer
        // declares a yield is not offered the launch path.
        if (accountUi.showsYield) {
            AccountSummaryRow(
                label = stringResource(Res.string.accounts_yield),
                amount = accountUi.yield,
                color = Income,
                onEditClick = variant.onLaunchYield.takeIf { accountUi.yieldsInterest },
                editTint = Income.copy(alpha = 0.5f),
            )
        }

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_expenses),
            amount = accountUi.expense,
            color = Expense,
        )

        if (accountUi.adjustment.value != 0.0) {
            AccountSummaryRow(
                label = stringResource(Res.string.accounts_adjustments),
                amount = accountUi.adjustment,
                color = Adjustment,
            )
        }

        if (accountUi.settlement.value != 0.0) {
            AccountSummaryRow(
                label = stringResource(Res.string.accounts_invoices),
                amount = accountUi.settlement,
                color = InvoicePayment,
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_balance),
            amount = accountUi.balance,
            color = colorScheme.onSurface,
            isTotal = true,
            onEditClick = variant.onEditBalance,
        )
    }
}

@Composable
private fun CompactContent(
    iconKey: String,
    name: String,
    isDefault: Boolean,
    variant: AccountCardVariant,
) {
    val formatter = LocalCurrencyFormatter.current

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
                imageVector = AppIcon.fromKey(iconKey).icon,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            if (isDefault) {
                Surface(
                    color = colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = colorScheme.primary,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.accounts_default),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (variant is AccountCardVariant.Dashboard) {
                Text(
                    text = formatter.format(variant.balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AccountSummaryRow(
    label: String,
    amount: DisplayAmount,
    color: Color,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    // Chrome by default, so the pencil reads the same on the rows where it is only a
    // way in. A row whose action *is* the figure — launching a yield — passes the
    // figure's colour, faded, so the two read as one thing without the control
    // competing with the number it creates.
    editTint: Color = colorScheme.onSurfaceVariant,
) {
    val formatter = LocalCurrencyFormatter.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = if (isTotal) 18.sp else 15.sp,
            fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isTotal) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (onEditClick != null) Modifier.clickable { onEditClick() } else Modifier
                ),
        ) {
            if (onEditClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ModeEdit,
                    contentDescription = null,
                    tint = editTint,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = formatter.format(amount),
                fontSize = if (isTotal) 20.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

