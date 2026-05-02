package com.neoutils.finsight.feature.creditCards.usecase

import arrow.core.Either
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.model.Invoice
import kotlinx.datetime.YearMonth

interface IGetOrCreateInvoiceForMonthUseCase {
    suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth
    ): Either<Throwable, Invoice>
}
