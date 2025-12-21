package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.YearMonth

class CreateInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(
        creditCardId: Long,
        openingMonth: YearMonth,
        closingMonth: YearMonth
    ): Result<Invoice> {
        if (closingMonth <= openingMonth) {
            return Result.failure(
                IllegalArgumentException("Closing month must be after opening month")
            )
        }

        val existingInvoices = invoiceRepository.getAllInvoicesByCreditCard(creditCardId)
        val overlappingInvoice = existingInvoices.find { existing ->
            openingMonth < existing.closingMonth && closingMonth > existing.openingMonth
        }

        if (overlappingInvoice != null) {
            return Result.failure(
                IllegalStateException(
                    "Invoice period overlaps with existing invoice " +
                    "(${overlappingInvoice.openingMonth} to ${overlappingInvoice.closingMonth})"
                )
            )
        }

        val invoice = Invoice(
            creditCardId = creditCardId,
            openingMonth = openingMonth,
            closingMonth = closingMonth,
            status = Invoice.Status.OPEN
        )

        val id = invoiceRepository.insert(invoice)
        return Result.success(invoice.copy(id = id))
    }
}
