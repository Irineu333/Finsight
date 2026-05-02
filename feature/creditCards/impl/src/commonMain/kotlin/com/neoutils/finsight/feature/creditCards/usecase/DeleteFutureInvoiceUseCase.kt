package com.neoutils.finsight.feature.creditCards.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.feature.creditCards.error.InvoiceError
import com.neoutils.finsight.feature.creditCards.error.InvoiceException
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.flow.first

class DeleteFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val operationRepository: IOperationRepository,
) {
    suspend operator fun invoke(invoiceId: Long): Either<InvoiceException, Unit> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status.isDeletable) {
            InvoiceException(InvoiceError.CannotDeleteInvoice)
        }

        operationRepository.observeOperationsBy(
            invoiceId = invoiceId,
        ).first().forEach { operation ->
            operationRepository.deleteOperationById(operation.id)
        }

        invoiceRepository.deleteById(invoiceId)
    }
}


