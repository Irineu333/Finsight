@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.creditCards.model

import com.neoutils.finsight.core.utils.extension.safeOnDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

data class InvoiceUi(
    val invoice: Invoice,
    val creditCard: CreditCard,
    val amount: Double,
    val totalUnpaidAmount: Double,
    val availableLimit: Double,
    val usagePercentage: Double,
    val showProgress: Boolean,
) {
    val id = invoice.id
    val status = invoice.status
    val openingMonth = invoice.openingMonth
    val closingMonth = invoice.closingMonth
    val dueMonth = invoice.dueMonth
    val openingDate: LocalDate = openingMonth.safeOnDay(creditCard.closingDay)
    val closingDate: LocalDate = closingMonth.safeOnDay(creditCard.closingDay)
    val dueDate: LocalDate = dueMonth.safeOnDay(creditCard.dueDay)
    val isClosable = (status.isOpen && currentDate >= closingDate) || status.isRetroactive
}
