package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class AdjustInvoiceUseCase(
    private val repository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) {
    suspend operator fun invoke(
        invoice: Invoice,
        target: Double,
        adjustmentDate: LocalDate
    ) {
        val currentInvoice = calculateInvoiceUseCase(invoiceId = invoice.id)

        if (target == currentInvoice) return

        val existingAdjustment = repository.getTransactionsBy(
            type = Transaction.Type.ADJUSTMENT,
            target = Transaction.Target.CREDIT_CARD,
            invoiceId = invoice.id,
            date = adjustmentDate
        ).firstOrNull()

        val difference = target - currentInvoice

        if (existingAdjustment == null) {
            operationRepository.createOperation(
                kind = Operation.Kind.TRANSACTION,
                title = null,
                date = adjustmentDate,
                categoryId = null,
                sourceAccountId = null,
                targetCreditCardId = invoice.creditCard.id,
                targetInvoiceId = invoice.id,
                transactions = listOf(
                    Transaction(
                        title = null,
                        type = Transaction.Type.ADJUSTMENT,
                        amount = -difference,
                        date = adjustmentDate,
                        target = Transaction.Target.CREDIT_CARD,
                        creditCard = invoice.creditCard,
                        invoice = invoice
                    )
                ),
            )
            return
        }

        val newAmount = existingAdjustment.amount - difference

        if (newAmount == 0.0) {
            val operationId = existingAdjustment.operationId
            if (operationId != null) {
                operationRepository.deleteOperationById(operationId)
            } else {
                repository.delete(existingAdjustment)
            }
            return
        }

        repository.update(
            existingAdjustment.copy(amount = newAmount)
        )
    }
}
