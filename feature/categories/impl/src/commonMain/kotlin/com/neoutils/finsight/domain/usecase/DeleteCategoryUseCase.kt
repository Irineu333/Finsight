package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository

/**
 * What the user calls "delete a category". A category is an `INCOME`/`EXPENSE`
 * account wearing a facade, so it retires through the same mechanism as any
 * other account: with movement it is closed, without it is removed.
 *
 * This did not exist — the ViewModel called the repository directly, with no
 * `Either`, no crashlytics, and an analytics event logged even on failure. And
 * removing the row cascaded through `budgets.categoryId`, destroying whole
 * budgets that merely happened to list the category first.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val closeAccountUseCase: CloseAccountUseCase,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = catch {
        requireNotNull(accountRepository.getAccountById(category.accountId)) {
            "Category ${category.id} has no chart-of-accounts row"
        }
    }.flatMap { account ->
        closeAccountUseCase(account).flatMap { outcome ->
            catch {
                // The facade only goes when its account did: an account with history
                // is closed, and a closed category keeps its own row.
                if (outcome == CloseAccountUseCase.Outcome.DELETED) {
                    categoryRepository.delete(category)
                }
            }
        }
    }
}
