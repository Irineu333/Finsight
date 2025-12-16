package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class GetInvoiceForTransactionUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(
        creditCardId: Long,
        transactionDate: LocalDate
    ): Invoice? {
        val month = transactionDate.yearMonth
        return invoiceRepository.getInvoiceForMonth(creditCardId, month)
    }
}
