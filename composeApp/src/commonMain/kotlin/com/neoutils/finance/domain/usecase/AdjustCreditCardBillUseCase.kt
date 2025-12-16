package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class AdjustCreditCardBillUseCase(
    private val repository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
) {
    suspend operator fun invoke(
        invoiceId: Long,
        targetBill: Double,
        adjustmentDate: LocalDate
    ) {
        val invoice = invoiceRepository.getById(invoiceId) ?: return

        val currentBill = calculateCreditCardBillUseCase(
            invoiceId = invoiceId,
            transactions = repository.getAllTransactions()
        )

        if (targetBill == currentBill) return

        val existingAdjustment = repository.getTransactionByTypeAndDate(
            type = Transaction.Type.ADJUSTMENT,
            date = adjustmentDate
        )?.takeIf { it.invoice?.id == invoiceId }

        val difference = targetBill - currentBill

        if (existingAdjustment == null) {
            repository.insert(
                Transaction(
                    type = Transaction.Type.ADJUSTMENT,
                    amount = difference,
                    title = "Ajuste de Fatura",
                    date = adjustmentDate,
                    target = Transaction.Target.CREDIT_CARD,
                    creditCard = creditCardRepository.getCreditCardById(invoice.creditCardId),
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
