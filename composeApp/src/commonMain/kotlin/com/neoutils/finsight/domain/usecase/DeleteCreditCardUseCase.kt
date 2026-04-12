package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IOperationRepository

class DeleteCreditCardUseCase(
    private val creditCardRepository: ICreditCardRepository,
    private val operationRepository: IOperationRepository,
) {
    suspend operator fun invoke(creditCard: CreditCard): Either<Throwable, Unit> = catch {
        // Delete only TRANSACTION-kind operations targeting this card.
        // PAYMENT operations (invoice payments) are preserved — their account
        // debit transaction remains valid even after the card is gone.
        operationRepository.deleteTransactionOperationsByCreditCard(creditCard.id)
        creditCardRepository.delete(creditCard)
    }
}
