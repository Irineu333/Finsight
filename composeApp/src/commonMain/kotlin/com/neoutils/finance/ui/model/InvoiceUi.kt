@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.model

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentMonth get() = Clock.System.now().toYearMonth()

data class InvoiceUi(
    val amount: Double,
    val totalUnpaidAmount: Double,
    val availableLimit: Double,
    val usagePercentage: Double,
    val showProgress: Boolean,
    val invoice: Invoice,
) {
    val id = invoice.id
    val isClosable = invoice.let { it.status.isOpen && currentMonth >= it.closingMonth }
    val status = invoice.status
    val closingMonth = invoice.closingMonth
    val dueMonth = invoice.dueMonth
}
