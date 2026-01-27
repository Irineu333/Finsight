package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finance.domain.error.InvoiceError
import com.neoutils.finance.domain.error.InvoiceException
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository

class DeleteFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
) {
    suspend operator fun invoke(invoiceId: Long): Either<InvoiceException, Unit> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status.isDeletable) {
            InvoiceException(InvoiceError.CannotDeleteInvoice)
        }

        transactionRepository.getTransactionsBy(
            type = null,
            target = null,
            date = null,
            invoiceId = invoiceId
        ).forEach { transaction ->
            transactionRepository.delete(transaction)
        }

        invoiceRepository.deleteById(invoiceId)
    }
}


