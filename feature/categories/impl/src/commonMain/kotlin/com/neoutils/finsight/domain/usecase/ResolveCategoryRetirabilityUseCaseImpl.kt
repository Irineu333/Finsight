package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

class ResolveCategoryRetirabilityUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
) : ResolveCategoryRetirabilityUseCase {

    override suspend fun invoke(categoryId: Long): Either<Throwable, CategoryRetirability> = either {
        // Resolved here and not received: the guards below decide on the dependents as
        // they are at this instant, so a screen that loaded the category minutes ago
        // cannot be told a delete is available that the domain would now refuse.
        val category = ensureNotNull(catch { categoryRepository.getCategoryById(categoryId) }.bind()) {
            CategoryException(CategoryError.NOT_FOUND)
        }

        catch {
            when {
                entryRepository.hasEntriesForDimension(category.dimensionId) ->
                    CategoryRetirability.MustArchive(RetireError.HAS_TRANSACTIONS)

                budgetRepository.hasBudgetForCategory(category.id) ->
                    CategoryRetirability.MustArchive(RetireError.HAS_BUDGET)

                recurringRepository.hasRecurringForCategory(category.id) ->
                    CategoryRetirability.MustArchive(RetireError.HAS_RECURRING)

                category.systemKey == SystemCategoryKey.YIELD && accountRepository.hasYieldingAccount() ->
                    CategoryRetirability.MustArchive(RetireError.HAS_YIELDING_ACCOUNTS)

                else -> CategoryRetirability.Deletable
            }
        }.bind()
    }
}
