package com.neoutils.finsight.ui.component

import androidx.compose.animation.animateContentSize
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
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.LocalCurrencyFormatter
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
import com.neoutils.finsight.resources.accounts_initial_balance
import com.neoutils.finsight.resources.accounts_invoices
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

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
        val onEditInitialBalance: () -> Unit,
    ) : AccountCardVariant()
}

@Composable
fun AccountCard(
    account: Account,
    variant: AccountCardVariant,
    modifier: Modifier = Modifier,
) {
    val isDetail = variant is AccountCardVariant.Detail
    val isSelection = variant is AccountCardVariant.Selection

    val containerColor = if (isSelection && (variant as AccountCardVariant.Selection).selected) {
        colorScheme.primaryContainer
    } else {
        colorScheme.surfaceContainer
    }

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
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (isDetail) {
            DetailContent(
                account = account,
                variant = variant as AccountCardVariant.Detail,
            )
        } else {
            CompactContent(
                account = account,
                variant = variant,
            )
        }
    }
}

@Composable
private fun DetailContent(
    account: Account,
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
                            imageVector = AppIcon.fromKey(account.iconKey).icon,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (account.isDefault) {
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
            label = stringResource(Res.string.accounts_initial_balance),
            amount = accountUi.initialBalance,
            color = colorScheme.onSurface,
            signDisplay = AccountSignDisplay.SHOW_ONLY_NEGATIVE,
            onEditClick = variant.onEditInitialBalance,
        )

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_income),
            amount = accountUi.income,
            color = Income,
            signDisplay = AccountSignDisplay.ALWAYS_POSITIVE,
        )

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_expenses),
            amount = accountUi.expense,
            color = Expense,
            signDisplay = AccountSignDisplay.ALWAYS_NEGATIVE,
        )

        if (accountUi.adjustment != 0.0) {
            AccountSummaryRow(
                label = stringResource(Res.string.accounts_adjustments),
                amount = accountUi.adjustment,
                color = Adjustment,
                signDisplay = AccountSignDisplay.SHOW_ALWAYS,
            )
        }

        if (accountUi.invoicePayment != 0.0) {
            AccountSummaryRow(
                label = stringResource(Res.string.accounts_invoices),
                amount = accountUi.invoicePayment,
                color = InvoicePayment,
                signDisplay = AccountSignDisplay.ALWAYS_NEGATIVE,
            )
        }

        if (accountUi.advancePayment != 0.0) {
            AccountSummaryRow(
                label = stringResource(Res.string.accounts_advance_payments),
                amount = accountUi.advancePayment,
                color = InvoicePayment,
                signDisplay = AccountSignDisplay.ALWAYS_NEGATIVE,
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

        AccountSummaryRow(
            label = stringResource(Res.string.accounts_balance),
            amount = accountUi.balance,
            color = colorScheme.onSurface,
            isTotal = true,
            onEditClick = variant.onEditBalance,
            signDisplay = AccountSignDisplay.SHOW_ONLY_NEGATIVE,
        )
    }
}

@Composable
private fun CompactContent(
    account: Account,
    variant: AccountCardVariant,
) {
    val formatter = LocalCurrencyFormatter.current
    val selected = (variant as? AccountCardVariant.Selection)?.selected == true
    val contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

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
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )

            if (account.isDefault) {
                val badgeColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.primary
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    contentColor = badgeColor,
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
                text = account.name,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
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
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    signDisplay: AccountSignDisplay = AccountSignDisplay.SHOW_ONLY_NEGATIVE,
    isTotal: Boolean = false,
    onEditClick: (() -> Unit)? = null,
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

        val formattedAmount = when (signDisplay) {
            AccountSignDisplay.ALWAYS_POSITIVE -> "+${formatter.format(amount.absoluteValue)}"
            AccountSignDisplay.ALWAYS_NEGATIVE -> "-${formatter.format(amount.absoluteValue)}"
            AccountSignDisplay.SHOW_ONLY_NEGATIVE -> formatter.format(amount)
            AccountSignDisplay.SHOW_ALWAYS -> formatter.formatWithSign(amount)
        }

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
                    tint = color.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = formattedAmount,
                fontSize = if (isTotal) 20.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

private enum class AccountSignDisplay {
    ALWAYS_POSITIVE,
    ALWAYS_NEGATIVE,
    SHOW_ONLY_NEGATIVE,
    SHOW_ALWAYS,
}
