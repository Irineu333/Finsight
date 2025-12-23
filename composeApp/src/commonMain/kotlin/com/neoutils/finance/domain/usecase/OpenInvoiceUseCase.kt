@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.OpenInvoiceErrors
import com.neoutils.finance.domain.exception.OpenInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth

private val errors = OpenInvoiceErrors()

class OpenInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository
) {
    suspend operator fun invoke(creditCardId: Long, openingMonth: YearMonth): Result<Invoice> {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId)
            ?: return Result.failure(OpenInvoiceException(errors.creditCardNotFound))

        val closingMonth = openingMonth.plusMonth()

        val existingInvoices = invoiceRepository.getAllInvoicesByCreditCard(creditCardId)

        val overlappingInvoice = existingInvoices.find { existing ->
            openingMonth < existing.closingMonth && closingMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return Result.failure(
                OpenInvoiceException(errors.overlappingInvoice)
            )
        }

        val invoice = Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            status = Invoice.Status.OPEN
        )

        return Result.success(
            invoice.copy(id = invoiceRepository.insert(invoice))
        )
    }
}
