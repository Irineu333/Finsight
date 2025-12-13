@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime

class AdjustCreditCardBillUseCase(
    private val repository: ITransactionRepository,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase
) {
    suspend operator fun invoke(
        creditCardId: Long,
        targetBill: Double,
        adjustmentDate: LocalDate
    ) {
        val currentBill = calculateCreditCardBillUseCase(
            creditCardId = creditCardId,
            target = adjustmentDate.yearMonth,
            transactions = repository.getAllTransactions()
        )

        if (targetBill == currentBill) return

        val existingAdjustment = repository.getTransactionByTypeAndDate(
            type = Transaction.Type.ADJUSTMENT,
            date = adjustmentDate
        )?.takeIf { it.target.isCreditCard && it.creditCardId == creditCardId }

        val difference = targetBill - currentBill

        if (existingAdjustment == null) {
            repository.insert(
                Transaction(
                    type = Transaction.Type.ADJUSTMENT,
                    amount = difference,
                    title = "Ajuste de Fatura",
                    date = adjustmentDate,
                    target = Transaction.Target.CREDIT_CARD,
                    creditCardId = creditCardId
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

