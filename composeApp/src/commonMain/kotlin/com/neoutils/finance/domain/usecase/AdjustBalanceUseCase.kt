package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class AdjustBalanceUseCase(
    private val repository: ITransactionRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
) {
    suspend operator fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        account: Account
    ) {
        val currentBalance = calculateBalanceUseCase(
            target = adjustmentDate.yearMonth,
            accountId = account.id,
        )

        if (targetBalance == currentBalance) return

        val existingAdjustment = repository.getTransactionsBy(
            type = Transaction.Type.ADJUSTMENT,
            target = Transaction.Target.ACCOUNT,
            date = adjustmentDate,
            accountId = account.id,
        ).firstOrNull()

        val difference = targetBalance - currentBalance

        if (existingAdjustment == null) {
            repository.insert(
                Transaction(
                    title = null,
                    type = Transaction.Type.ADJUSTMENT,
                    amount = difference,
                    date = adjustmentDate,
                    account = account,
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
