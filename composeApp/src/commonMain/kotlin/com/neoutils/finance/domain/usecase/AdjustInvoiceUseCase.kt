package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class AdjustInvoiceUseCase(
    private val repository: ITransactionRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        target: Double,
        adjustmentDate: LocalDate
    ) {
        val invoice = invoiceRepository.getInvoiceById(invoiceId) ?: return

        val currentInvoice = calculateInvoiceUseCase(invoiceId = invoiceId)

        if (target == currentInvoice) return

        val existingAdjustment = repository.getTransactionsBy(
            type = Transaction.Type.ADJUSTMENT,
            target = Transaction.Target.CREDIT_CARD,
            invoiceId = invoiceId,
            date = adjustmentDate
        ).firstOrNull()

        val difference = target - currentInvoice

        if (existingAdjustment == null) {
            repository.insert(
                Transaction(
                    title = null,
                    type = Transaction.Type.ADJUSTMENT,
                    amount = difference,
                    date = adjustmentDate,
                    target = Transaction.Target.CREDIT_CARD,
                    creditCard = invoice.creditCard,
                    invoice = invoice
                )
            )
            return
        }

        val newAmount = existingAdjustment.amount + difference

        if (newAmount == 0.0) {
            repository.delete(existingAdjustment)
            return
        }

        repository.update(
            existingAdjustment.copy(amount = newAmount)
        )
    }
}
