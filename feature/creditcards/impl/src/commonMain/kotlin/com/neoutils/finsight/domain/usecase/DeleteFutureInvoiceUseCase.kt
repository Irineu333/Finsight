package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.first

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

        transactionRepository.observeTransactionsBy(
            dimensionId = invoice.dimensionId,
        ).first().forEach { transaction ->
            transactionRepository.deleteTransactionById(transaction.id)
        }

        invoiceRepository.deleteById(invoiceId)
    }
}


