@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.extension.effectiveDay
import com.neoutils.finsight.extension.yearMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

class AddCreditCardUseCase(
    private val repository: ICreditCardRepository,
    private val openInvoiceUseCase: OpenInvoiceUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
) {
    suspend operator fun invoke(
        form: CreditCardForm
    ): Either<Throwable, CreditCard> {
        return either {
            validateCreditCardName(
                form.name
            ).mapLeft {
                CreditCardException(it)
            }.bind()

            val creditCard = form.build().bind()

             catch {
                creditCard.copy(
                    id = repository.insert(creditCard)
                )
            }.bind()
        }.onRight { creditCard ->
            val closingDay = currentDate.yearMonth.effectiveDay(creditCard.closingDay)

            val openingMonth = if (currentDate.day < closingDay) {
                currentDate.yearMonth.minusMonth()
            } else {
                currentDate.yearMonth
            }

            openInvoiceUseCase(
                creditCardId = creditCard.id,
                openingMonth = openingMonth
            )
        }
    }
}
