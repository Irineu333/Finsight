package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.YearMonth

/**
 * Brings an invoice into existence for a target due month.
 *
 * The single way an invoice is born from a month: the user's explicit gesture and the
 * on-demand creation of a transaction both come through here, so the two cannot produce
 * invoices that differ. What is declared is the *cycle*, never its value — the window and
 * the due month derive from the card, and what the invoice is worth comes later, from
 * entries or from a balance adjustment.
 *
 * The status is derived, never chosen: a month falling due before the open invoice's due
 * month is [Invoice.Status.RETROACTIVE], from it onwards [Invoice.Status.FUTURE]. The
 * reference is the open invoice and not today, so a card whose open invoice fell behind
 * keeps the same criterion without a special case. Opening remains exclusive to
 * [OpenInvoiceUseCase]: this operation never produces [Invoice.Status.OPEN].
 */
interface CreateInvoiceUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The card is resolved **when the operation runs**, because the window and the due
     * month are read off it; an identity that matches nothing is refused with
     * `InvoiceError.CreditCardNotFound` and no invoice is written.
     *
     * It answers the invoice as stored — id and dimension included — because a caller
     * that rebuilt it from the month would lose the dimension every leg is tagged with.
     */
    suspend operator fun invoke(
        creditCardId: Long,
        dueMonth: YearMonth,
    ): Either<Throwable, Invoice>

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        creditCard: CreditCard,
        dueMonth: YearMonth,
    ): Either<Throwable, Invoice> = invoke(creditCard.id, dueMonth)
}
