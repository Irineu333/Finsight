package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository

class ArchiveCreditCardUseCaseImpl(
    private val creditCardRepository: ICreditCardRepository,
    private val accountRepository: IAccountRepository,
    private val archiveAccountUseCase: ArchiveAccountUseCase,
) : ArchiveCreditCardUseCase {

    override suspend fun invoke(creditCardId: Long): Either<Throwable, Unit> = either {
        val creditCard = ensureNotNull(
            catch { creditCardRepository.getCreditCardById(creditCardId) }.bind()
        ) {
            CreditCardException(CreditCardError.NOT_FOUND)
        }

        val account = ensureNotNull(
            catch { accountRepository.getAccountById(creditCard.accountId) }.bind()
        ) {
            AccountException(AccountError.NOT_FOUND)
        }

        archiveAccountUseCase(account.id).bind()
    }
}
