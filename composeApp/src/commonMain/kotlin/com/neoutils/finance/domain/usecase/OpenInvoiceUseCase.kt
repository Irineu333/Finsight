@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus

class OpenInvoiceUseCase(
        private val invoiceRepository: IInvoiceRepository,
        private val creditCardRepository: ICreditCardRepository
) {
        suspend operator fun invoke(creditCardId: Long, openingMonth: YearMonth): Result<Invoice> {
                creditCardRepository.getCreditCardById(creditCardId)
                        ?: return Result.failure(IllegalArgumentException("Credit card not found"))

                val closingMonth = openingMonth.plus(1, DateTimeUnit.MONTH)

                // Check for overlapping invoices
                val existingInvoices = invoiceRepository.getAllInvoicesByCreditCard(creditCardId)
                val overlappingInvoice =
                        existingInvoices.find { existing ->
                                openingMonth < existing.closingMonth &&
                                        closingMonth > existing.openingMonth
                        }

                if (overlappingInvoice != null) {
                        return Result.failure(
                                IllegalStateException(
                                        "Invoice period overlaps with existing invoice " +
                                                "(${overlappingInvoice.openingMonth} to ${overlappingInvoice.closingMonth})"
                                )
                        )
                }

                val invoice =
                        Invoice(
                                creditCardId = creditCardId,
                                openingMonth = openingMonth,
                                closingMonth = closingMonth,
                                status = Invoice.Status.OPEN
                        )

                val id = invoiceRepository.insert(invoice)
                return Result.success(invoice.copy(id = id))
        }
}
