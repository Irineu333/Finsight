package com.neoutils.finsight.extension

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.LocalDateFormats
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun Invoice.Status.toUiText(): StringResource = when (this) {
    Invoice.Status.FUTURE -> Res.string.invoice_status_future
    Invoice.Status.OPEN -> Res.string.invoice_status_open
    Invoice.Status.CLOSED -> Res.string.invoice_status_closed
    Invoice.Status.PAID -> Res.string.invoice_status_paid
    Invoice.Status.RETROACTIVE -> Res.string.invoice_status_retroactive
}

/**
 * The verb that names the payment this invoice offers.
 *
 * "Advance" only while the cycle is still taking spending, because only there is there
 * something to anticipate; once it has ended — and a past cycle ended longer ago still —
 * it is simply paying. One owner, read by every surface that offers the command and by
 * the sheet that carries it out, so the wording cannot drift from the mode.
 *
 * Meaningful only where `Invoice.acceptsPayment` holds.
 */
val Invoice.paymentLabel: StringResource
    get() = if (acceptsFullSettlement) {
        Res.string.invoice_payment_pay
    } else {
        Res.string.invoice_payment_advance
    }

/**
 * How an invoice names itself: the month it is due in, and what state it is in.
 *
 * Takes the two facts rather than the invoice, so a surface that carries them flat —
 * a leg card, whose model holds no domain graph — reads the same label as one that
 * holds the whole facade.
 */
@Composable
fun invoiceLabel(dueMonth: YearMonth, status: Invoice.Status): String {
    val formats = LocalDateFormats.current
    val statusLabel = stringResource(status.toUiText())
    return "${formats.yearMonth.format(dueMonth)} • $statusLabel"
}

@Composable
fun Invoice.toLabel(): String = invoiceLabel(dueMonth, status)

@Composable
fun InvoiceMonthSelection.toLabel(): String {
    val formats = LocalDateFormats.current
    val newLabel = stringResource(Res.string.invoice_status_new)
    return existingInvoice?.toLabel() ?: "${formats.yearMonth.format(dueMonth)} • $newLabel"
}
