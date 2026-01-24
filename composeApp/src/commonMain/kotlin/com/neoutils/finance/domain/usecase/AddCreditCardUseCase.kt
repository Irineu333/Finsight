@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.extension.effectiveDay
import com.neoutils.finance.extension.then
import com.neoutils.finance.extension.yearMonth
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
    ): Result<CreditCard> {

        return validateCreditCardName(form.name)
            .then { form.build() }
            .map { creditCard ->
                creditCard.copy(
                    id = repository.insert(creditCard)
                )
            }
            .onSuccess { creditCard ->
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
