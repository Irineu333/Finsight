package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

/**
 * Pays a closed invoice in full — the money leaving an account of the user's choice,
 * and the invoice moving to paid, as one decision.
 *
 * **The paying account may be in another currency, and then the caller states what
 * leaves it** — the invoice's own side stays exactly what is owed, in the card's
 * currency, because that is a fact and not a choice. No rate is a parameter here or
 * anywhere on this path: it is the quotient of the two ends, derived and archived by
 * the domain afterwards (design D6).
 *
 * **Public contract.** An interface rather than a concrete class because the
 * implementation depends on a use case that stays internal to the feature — the
 * lifecycle transition that marks the invoice paid — which is pattern 2 of
 * `feature/README.md`. Identities, a date and an [Account] in; the paid [Invoice] out.
 * No presentation type crosses this boundary: failures arrive as `Throwable`, in
 * practice `InvoiceException` carrying an `InvoiceError` with an English `message` for
 * logs, and turning one into a sentence is an extension a screen opts into.
 *
 * The error channel is `Throwable` rather than `InvoiceException` on purpose: the
 * ledger's write boundary throws its own refusals (an archived paying account, for
 * one), and `either {}` does not intercept a thrown exception — typing the channel
 * narrower would let those escape the `Either` and crash the caller.
 */
interface PayInvoicePaymentUseCase {
    /**
     * @param paidAmount what leaves [account], when it is not what the invoice owes.
     * `null` is the same-currency case.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Invoice>
}
