package com.neoutils.finsight.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.invoiceLabel
import com.neoutils.finsight.ui.extension.color
import com.neoutils.finsight.ui.model.LegTone
import com.neoutils.finsight.ui.model.TransactionLegUi
import com.neoutils.finsight.ui.theme.Adjustment
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income
import com.neoutils.finsight.util.stringUiText

/**
 * One monetary leg of an operation, as the three answers it owes at once: **what
 * happened** to that money (the verb, above), **whose** money it is (the account or
 * the card) and **how much** (on the same line as the name, so a one-leg operation
 * costs one line more than the row it replaced).
 *
 * The invoice and the instalment live here, and not beside the operation's context
 * lines, because they are attributes of *this* leg: the invoice is the dimension the
 * liability leg carries, and an instalment's total is denominated by that same card.
 *
 * It derives nothing. Verb, sign and order arrive resolved from `toTransactionLegs`.
 *
 * @param valueTestTag marks the figure, so a flow can assert the amount of a
 * specific card rather than of whichever one renders first.
 */
@Composable
fun TransactionLegCard(
    leg: TransactionLegUi,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
) {
    val formatter = LocalCurrencyFormatter.current
    val onClick = leg.onClick
    // The direction, in the axis a glance reads. It repeats the verb on purpose: the
    // figure carries no sign here, so colour is what tells two cards of the same
    // operation apart before either is read.
    val tone = leg.tone.color()

    Surface(
        color = tone.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringUiText(leg.verb),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = tone,
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // `American · USD`, and only where two currencies are on the same
                    // screen — the doctrine `AccountSelector` established. The code and
                    // not the symbol: here the currency is being identified, which is
                    // the one job a symbol does badly (three of them write `kr`).
                    text = leg.currencyCode?.let { "${leg.name} · $it" } ?: leg.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatter.format(leg.amount),
                    modifier = valueTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tone,
                )
            }

            leg.invoice?.let { invoice ->
                Text(
                    text = invoiceLabel(invoice.dueMonth, invoice.status),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = invoice.status.color,
                )
            }

            leg.installment?.let { installment ->
                Text(
                    text = "${installment.label} • ${formatter.format(installment.total)}",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What sits between two leg cards: the rate the operation applied, and the arrow
 * that says which way it was applied.
 *
 * The rate *is* a relation between the two legs, so it is drawn where it is one. The
 * quotient arrives formatted — the grammar of a rate belongs to whoever states rates
 * — and the arrow needs no separate assertion of direction: the first card is the
 * leg money left, by the same definition the rate divides in.
 *
 * An operation in a single currency has nothing to divide by, and draws no
 * connector at all.
 */
@Composable
fun TransactionLegConnector(
    rate: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = rate,
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The palette of a direction: the same three colours the item surface reads, because
 * "money left here" is the same statement whether a row or a card makes it.
 */
private fun LegTone.color(): Color = when (this) {
    LegTone.OUTGOING -> Expense
    LegTone.INCOMING -> Income
    LegTone.ADJUSTMENT -> Adjustment
}
