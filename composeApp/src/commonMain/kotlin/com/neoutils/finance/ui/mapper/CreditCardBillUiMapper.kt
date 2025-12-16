package com.neoutils.finance.ui.mapper

import androidx.compose.ui.graphics.Color
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.model.CreditCardBillUi

class CreditCardBillUiMapper {

    fun toUi(
        bill: Double,
        limit: Double,
        invoiceStatus: Invoice.Status? = null
    ): CreditCardBillUi {

        val displayBill = bill.coerceAtLeast(0.0)
        val availableLimit = (limit - bill).coerceAtLeast(0.0)

        val statusLabel = invoiceStatus?.label ?: ""
        val statusColor = when (invoiceStatus) {
            Invoice.Status.OPEN -> Color(0xFFFFA726)
            Invoice.Status.CLOSED -> Color(0xFFEF5350)
            Invoice.Status.PAID -> Color(0xFF66BB6A)
            null -> Color.Unspecified
        }

        if (displayBill > 0 && limit > 0) {
            return CreditCardBillUi(
                bill = displayBill.toMoneyFormat(),
                limit = limit.toMoneyFormat(),
                availableLimit = availableLimit.toMoneyFormat(),
                usagePercentage = (bill / limit).coerceIn(0.0, 1.0),
                showProgress = true,
                statusLabel = statusLabel,
                statusColor = statusColor
            )
        }

        return CreditCardBillUi(
            bill = displayBill.toMoneyFormat(),
            limit = limit.toMoneyFormat(),
            availableLimit = availableLimit.toMoneyFormat(),
            usagePercentage = 0.0,
            showProgress = false,
            statusLabel = statusLabel,
            statusColor = statusColor
        )
    }
}

