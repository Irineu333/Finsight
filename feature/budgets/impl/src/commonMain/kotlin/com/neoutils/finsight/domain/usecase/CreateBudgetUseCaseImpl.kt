@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.BudgetException
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IBudgetRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateBudgetUseCaseImpl(
    private val budgetRepository: IBudgetRepository,
    private val validateBudgetTitle: ValidateBudgetTitleUseCase,
) : CreateBudgetUseCase {

    override suspend fun invoke(
        title: String,
        categories: List<Category>,
        iconKey: String,
        currency: String,
        limitType: LimitType,
        amount: Double,
        percentage: Double?,
        baseIncome: Recurring?,
    ): Either<Throwable, Budget> = either {
        // The validator is the single owner of "what a budget title is", trimming
        // included — the stored title is the one it returns, never the raw input.
        val validTitle = validateBudgetTitle(title).mapLeft(::BudgetException).bind()
        val limit = budgetLimit(limitType, amount, percentage, baseIncome).bind()

        val budget = Budget(
            title = validTitle,
            categories = categories,
            iconKey = iconKey,
            amount = limit.amount,
            currency = currency,
            limitType = limitType,
            percentage = limit.percentage,
            recurringId = limit.recurringId,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )

        catch { budget.copy(id = budgetRepository.insert(budget)) }.bind()
    }
}
