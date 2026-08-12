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

class AddCreditCardUseCase(
    private val repository: ICreditCardRepository,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val clock: Clock,
) {

    private val currentDate get() = clock.today()

    /**
     * A card is born with its first invoice open, on the cycle it is already in today.
     * Opening it is part of the creation and not a follow-up to it: a card whose invoice
     * failed to open would accept no expense at all, so the failure fails the creation
     * rather than being reported as success.
     *
     * @param currency what the card's `LIABILITY` account is denominated in — chosen in
     * the form, carried explicitly, and fixed from the moment the card exists (D12).
     */
    suspend operator fun invoke(
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
                creditCard = persisted,
                // The card opens on the cycle it is already in today.
                openingMonth = persisted.invoiceWindowOn(currentDate).openingMonth
            ).bind()

            persisted
        }
    }
}
