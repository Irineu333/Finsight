package com.neoutils.finsight.feature.budgets.usecase

import com.neoutils.finsight.feature.budgets.model.Budget
import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.core.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface ICalculateBudgetProgressUseCase {
    operator fun invoke(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        recurringList: List<Recurring> = emptyList(),
        operations: List<Operation> = emptyList(),
        today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    ): List<BudgetProgress>
}
