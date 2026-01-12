package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.exception.DeleteFutureInvoiceException
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository

class DeleteFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
) {
    suspend operator fun invoke(invoiceId: Long): Result<Unit> {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)
            ?: return Result.failure(DeleteFutureInvoiceException("Fatura não encontrada"))

        if (invoice.status != Invoice.Status.FUTURE) {
            return Result.failure(DeleteFutureInvoiceException("Apenas faturas futuras podem ser excluídas"))
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

        return Result.success(Unit)
    }
}


