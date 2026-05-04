package com.neoutils.finsight.feature.creditCards.usecase

import arrow.core.Either
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import kotlinx.datetime.YearMonth

interface IGetOrCreateInvoiceForMonthUseCase {
    suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth
    ): Either<Throwable, Invoice>
}
