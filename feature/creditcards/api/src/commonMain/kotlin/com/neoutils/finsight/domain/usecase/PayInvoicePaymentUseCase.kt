package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * Pays a closed invoice in full: it writes the payment **and** marks the invoice paid.
 *
 * **The paying account may be in another currency, and then the caller states what
 * leaves it** — the invoice's own side stays exactly what is owed, in the card's
 * currency, because that is a fact and not a choice. No rate is a parameter: it is the
 * quotient of the two ends, derived afterwards (design D6). The write boundary posts the
 * residue of each currency to that currency's conversion account, **without** the
 * invoice's dimension: the exchange result does not belong to the invoice, and copying
 * the dimension onto it would have the whole transaction refused (design D15).
 */
interface PayInvoicePaymentUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * Both identities are resolved **when the operation runs**: an invoice that matches
     * nothing is refused with `InvoiceError.NotFound`, a paying account that matches
     * nothing with `AccountError.NOT_FOUND`, and neither writes anything.
     *
     * @param paidAmount what leaves the account, when it is not what the invoice owes.
     * `null` is the same-currency case.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double? = null,
    ): Either<Throwable, Invoice>

    /**
     * The convenience for a caller that already holds the paying account. It extracts
     * the identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Invoice> = invoke(
        invoiceId = invoiceId,
        date = date,
        accountId = account.id,
        paidAmount = paidAmount,
    )
}
