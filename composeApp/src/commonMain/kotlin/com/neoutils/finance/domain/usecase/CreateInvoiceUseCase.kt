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

        val existingByOpening = invoiceRepository.getByOpeningMonth(creditCardId, openingMonth)
        if (existingByOpening != null) {
            return Result.failure(
                IllegalStateException("An invoice with this opening month already exists")
            )
        }

        val existingByClosing = invoiceRepository.getByClosingMonth(creditCardId, closingMonth)
        if (existingByClosing != null) {
            return Result.failure(
                IllegalStateException("An invoice with this closing month already exists")
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
