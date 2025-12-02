package com.neoutils.finance.usecase

import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.datetime.LocalDate

class AdjustBalanceUseCase(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(
        currentBalance: Double,
        targetBalance: Double,
        adjustmentDate: LocalDate
    ) {

        if (targetBalance == currentBalance) return

        val existingAdjustment = repository.getTransactionByTypeAndDate(
            type = TransactionEntry.Type.ADJUSTMENT,
            date = adjustmentDate
        )

        val difference = targetBalance - currentBalance

        if (existingAdjustment == null) {
            repository.insert(
                TransactionEntry(
                    type = TransactionEntry.Type.ADJUSTMENT,
                    amount = difference,
                    title = "Ajuste de Saldo",
                    date = adjustmentDate
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
