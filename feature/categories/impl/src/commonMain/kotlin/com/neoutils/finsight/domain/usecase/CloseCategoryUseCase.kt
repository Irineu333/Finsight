package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase

/**
 * Retires a category that has movement. The facade stays so past transactions
 * keep showing its name; only its ledger account is closed, which is what removes
 * it from the pickers and from `Budget.categories`.
 */
class CloseCategoryUseCase(
    private val accountRepository: IAccountRepository,
    private val closeAccountUseCase: CloseAccountUseCase,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = catch {
        accountRepository.getAccountById(category.accountId)
    }.flatMap { account ->
        if (account == null) return@flatMap AccountException(AccountError.NOT_FOUND).left()
        closeAccountUseCase(account)
    }
}
