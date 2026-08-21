package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.YearMonth

/**
 * Finds the invoice a target month already has, or has [CreateInvoiceUseCase] make it.
 *
 * What is its own is the lookup and the refusal of an invoice closed to new spending.
 * Classifying the created invoice is not: that rule has a single owner, in the creation
 * itself, which is what makes an invoice born from a transaction indistinguishable from
 * one born from the user's gesture.
 */
interface GetOrCreateInvoiceForMonthUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The card is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `InvoiceError.CreditCardNotFound` and nothing is created.
     */
    suspend operator fun invoke(
        creditCardId: Long,
        targetDueMonth: YearMonth,
    ): Either<Throwable, Invoice>

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        creditCard: CreditCard,
        targetDueMonth: YearMonth,
    ): Either<Throwable, Invoice> = invoke(creditCard.id, targetDueMonth)
}
