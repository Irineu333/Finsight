@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.model.invoiceWindowOn
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.extension.today
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddCreditCardUseCaseImpl(
    private val repository: ICreditCardRepository,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val clock: Clock,
) : AddCreditCardUseCase {

    private val currentDate get() = clock.today()

    override suspend fun invoke(
        form: CreditCardForm,
        currency: String,
    ): Either<Throwable, CreditCard> {
        return either {
            validateCreditCardName(
                form.name
            ).mapLeft {
                CreditCardException(it)
            }.bind()

            val creditCard = form.build().bind()

            val persisted = catch {
                creditCard.copy(
                    id = repository.insert(creditCard, currency)
                )
            }.bind()

            openInvoiceUseCase(
                creditCardId = persisted.id,
                // The card opens on the cycle it is already in today.
                openingMonth = persisted.invoiceWindowOn(currentDate).openingMonth
            ).bind()

            persisted
        }
    }
}
