package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository

class GetCurrentInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository
) {
    suspend operator fun invoke(creditCardId: Long): Invoice? {
        return invoiceRepository.getLatestUnpaidInvoice(creditCardId)
    }
}
