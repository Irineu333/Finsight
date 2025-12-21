@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateCurrentInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {
    suspend operator fun invoke(creditCardId: Long): Invoice? {
        creditCardRepository.getCreditCardById(creditCardId) ?: return null

        val currentMonth = Clock.System.now().toYearMonth()
        val nextMonth = currentMonth.plus(1, DateTimeUnit.MONTH)

        val existingInvoices = invoiceRepository.getAllInvoicesByCreditCard(creditCardId)
        val overlappingInvoice = existingInvoices.find { existing ->
            currentMonth < existing.closingMonth && nextMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return overlappingInvoice
        }

        val newInvoice = Invoice(
            creditCardId = creditCardId,
            openingMonth = currentMonth,
            closingMonth = nextMonth,
            status = Invoice.Status.OPEN
        )

        val id = invoiceRepository.insert(newInvoice)
        return newInvoice.copy(id = id)
    }
}
