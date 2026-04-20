@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Invoice
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

data class InvoiceUi(
    val amount: Double,
    val totalUnpaidAmount: Double,
    val availableLimit: Double,
    val usagePercentage: Double,
    val showProgress: Boolean,
    val invoice: Invoice,
    val closingDate: LocalDate,
) {
    val id = invoice.id
    val creditCard = invoice.creditCard
    val isClosable = (invoice.status.isOpen && currentDate >= closingDate) || invoice.status.isRetroactive
    val status = invoice.status
    val closingMonth = invoice.closingMonth
    val dueMonth = invoice.dueMonth
    val dueDate = invoice.dueDate
}
