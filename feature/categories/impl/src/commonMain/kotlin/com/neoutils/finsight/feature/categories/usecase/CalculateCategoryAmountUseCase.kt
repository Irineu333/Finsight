package com.neoutils.finsight.feature.categories.usecase

import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import kotlinx.datetime.YearMonth

class CalculateCategoryAmountUseCase(
    private val transactionRepository: ITransactionRepository,
) {
    data class Result(
        val totalAmount: Double,
        val transactionCount: Int,
    )

    suspend operator fun invoke(categoryId: Long, yearMonth: YearMonth): Result {
        val transactions = transactionRepository.getTransactionsByCategoryAndDateRange(
            categoryId = categoryId,
            startDate = yearMonth.safeOnDay(1),
            endDate = yearMonth.safeOnDay(Int.MAX_VALUE),
        )
        return Result(
            totalAmount = transactions.sumOf { it.amount },
            transactionCount = transactions.size,
        )
    }
}
