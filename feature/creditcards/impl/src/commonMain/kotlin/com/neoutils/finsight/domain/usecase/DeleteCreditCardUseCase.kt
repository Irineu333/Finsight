package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase

/**
 * What the user calls "delete a card". The card's ledger account is retired by
 * the one mechanism that retires any account, so a card now behaves exactly like
 * a plain account: with movement it is closed, without it is removed.
 *
 * It used to bulk-delete the card's purchases while preserving its payments, in
 * two steps without a transaction — leaving a `LIABILITY` account alive with no
 * facade and the invoice history half gone.
 */
class DeleteCreditCardUseCase(
    private val creditCardRepository: ICreditCardRepository,
    private val accountRepository: IAccountRepository,
    private val closeAccountUseCase: CloseAccountUseCase,
) {
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> = catch {
        requireNotNull(accountRepository.getAccountById(creditCard.accountId)) {
            "Credit card ${creditCard.id} has no chart-of-accounts row"
        }
    }.flatMap { account ->
        closeAccountUseCase(account).flatMap { outcome ->
            catch {
                if (outcome == CloseAccountUseCase.Outcome.DELETED) {
                    creditCardRepository.delete(creditCard)
                }
            }
        }
    }
}
