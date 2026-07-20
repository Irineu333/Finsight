package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase

/**
 * Retires a card that has movement. The facade row stays — it is what keeps the
 * card's name readable in the history that references it; only its ledger account
 * is closed, which is what removes the card from the active lists.
 */
class ArchiveCreditCardUseCase(
    private val accountRepository: IAccountRepository,
    private val archiveAccountUseCase: ArchiveAccountUseCase,
) {
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> = catch {
        accountRepository.getAccountById(creditCard.accountId)
    }.flatMap { account ->
        if (account == null) return@flatMap AccountException(AccountError.NOT_FOUND).left()
        archiveAccountUseCase(account)
    }
}
