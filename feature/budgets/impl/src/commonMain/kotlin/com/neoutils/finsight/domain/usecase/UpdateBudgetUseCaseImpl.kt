package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.exception.BudgetException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IBudgetRepository

class UpdateBudgetUseCaseImpl(
    private val budgetRepository: IBudgetRepository,
    private val validateBudgetTitle: ValidateBudgetTitleUseCase,
) : UpdateBudgetUseCase {

    override suspend fun invoke(
        budgetId: Long,
        title: String,
        categories: List<Category>,
        iconKey: String,
        limitType: LimitType,
        amount: Double,
        percentage: Double?,
        baseIncome: Recurring?,
    ): Either<Throwable, Unit> = either {
        // Resolved here and not received: the edit is applied to the budget as it is at
        // this instant, so everything the caller did not name — its denomination, its
        // creation instant — survives untouched.
        val budget = ensureNotNull(catch { budgetRepository.getBudgetById(budgetId) }.bind()) {
            BudgetException(BudgetError.NOT_FOUND)
        }

        // Its own identity is ignored by the uniqueness check, so an edit that keeps
        // the title is not refused as a clash with itself.
        val validTitle = validateBudgetTitle(title, ignoreId = budgetId)
            .mapLeft(::BudgetException)
            .bind()

        val limit = budgetLimit(limitType, amount, percentage, baseIncome).bind()

        catch {
            budgetRepository.update(
                // `currency` is deliberately absent from this copy: the denomination of
                // a stored limit never changes.
                budget.copy(
                    title = validTitle,
                    categories = categories,
                    iconKey = iconKey,
                    amount = limit.amount,
                    limitType = limitType,
                    percentage = limit.percentage,
                    recurringId = limit.recurringId,
                )
            )
        }.bind()
    }
}
