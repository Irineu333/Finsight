package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.YearMonth

/**
 * Puts a card's cycle on the air: the invoice opening on a month becomes the one new
 * spending lands in.
 *
 * A future invoice already declared for that month is promoted rather than duplicated;
 * otherwise the invoice is created open. An opening that would straddle a cycle the
 * card already has is refused with `InvoiceError.OverlappingInvoice` — a card is only
 * ever spending into one invoice.
 */
interface OpenInvoiceUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The card is resolved **when the operation runs**, because the closing and due
     * months are read off it; an identity that matches nothing is refused with
     * `InvoiceError.CreditCardNotFound` and nothing is opened.
     */
    suspend operator fun invoke(
        creditCardId: Long,
        openingMonth: YearMonth,
    ): Either<InvoiceException, Invoice>

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        creditCard: CreditCard,
        openingMonth: YearMonth,
    ): Either<InvoiceException, Invoice> = invoke(creditCard.id, openingMonth)
}
