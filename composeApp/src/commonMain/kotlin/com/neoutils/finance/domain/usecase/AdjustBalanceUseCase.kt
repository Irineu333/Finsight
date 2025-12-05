package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class AdjustBalanceUseCase(
    private val repository: ITransactionRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
) {
    suspend operator fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate
    ) {
        val currentBalance = calculateBalanceUseCase(
            target = adjustmentDate.yearMonth,
        )

        if (targetBalance == currentBalance) return

        val existingAdjustment = repository.getTransactionByTypeAndDate(
            type = Transaction.Type.ADJUSTMENT,
            date = adjustmentDate
        )

        val difference = targetBalance - currentBalance

        if (existingAdjustment == null) {
            repository.insert(
                Transaction(
                    type = Transaction.Type.ADJUSTMENT,
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
