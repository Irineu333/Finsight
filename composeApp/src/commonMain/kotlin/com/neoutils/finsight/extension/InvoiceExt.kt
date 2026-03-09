package com.neoutils.finsight.extension

import androidx.compose.runtime.Composable
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.LocalDateFormats
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun Invoice.Status.toUiText(): StringResource = when (this) {
    Invoice.Status.FUTURE -> Res.string.invoice_status_future
    Invoice.Status.OPEN -> Res.string.invoice_status_open
    Invoice.Status.CLOSED -> Res.string.invoice_status_closed
    Invoice.Status.PAID -> Res.string.invoice_status_paid
    Invoice.Status.RETROACTIVE -> Res.string.invoice_status_retroactive
}

@Composable
fun Invoice.toLabel(): String {
    val formats = LocalDateFormats.current
    val statusLabel = stringResource(status.toUiText())
    return "${formats.yearMonth.format(dueMonth)} • $statusLabel"
}

@Composable
fun InvoiceMonthSelection.toLabel(): String {
    val formats = LocalDateFormats.current
    val newLabel = stringResource(Res.string.invoice_status_new)
    return existingInvoice?.toLabel() ?: "${formats.yearMonth.format(dueMonth)} • $newLabel"
}
