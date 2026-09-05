package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate

/**
 * Pays part of an open invoice ahead of time.
 *
 * **`amount` is in the card's currency and always has been**, and that is what makes the
 * ceiling correct: `amount <= what the invoice owes` compares two figures denominated
 * the same way. When the paying account is denominated differently the caller adds
 * `paidAmount`, which is what leaves the *account* — and that side carries no ceiling at
 * all, because comparing it to the invoice would be comparing two currencies.
 *
 * It answers the transaction it wrote, so a caller can point at the payment it made.
 */
interface AdvanceInvoicePaymentUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * Both identities are resolved **when the operation runs**: an invoice that matches
     * nothing is refused with `InvoiceError.NotFound`, a paying account that matches
     * nothing with `AccountError.NOT_FOUND`, and neither writes anything.
     *
     * @param amount how much of the invoice is being settled, in the **card's** currency.
     * @param paidAmount what leaves the account, when it is denominated differently.
     * `null` is the same-currency case.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double? = null,
    ): Either<Throwable, Transaction>

    /**
     * The convenience for a caller that already holds the paying account. It extracts
     * the identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Transaction> = invoke(
        invoiceId = invoiceId,
        amount = amount,
        date = date,
        accountId = account.id,
        paidAmount = paidAmount,
    )
}
