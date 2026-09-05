package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

/**
 * Correcting a partial invoice payment that is already registered, in place.
 *
 * It is the counterpart of [AdvanceInvoicePaymentUseCase] and shares its rules wholesale
 * — a payment is no more or less admissible for having been written once already. What
 * differs is the write: the legs of an existing operation are rewritten rather than
 * created, so the operation keeps its identity instead of becoming a new one.
 *
 * **The mode is not redecided here.** Correcting a partial payment is reaffirming a
 * partial payment, which is why the rule that an invoice must still accept one is
 * inherited rather than relaxed: an invoice that stopped taking spending refuses the
 * correction too, whether or not a screen offers it. Nothing on this path marks an
 * invoice `PAID` — that belongs to the payment that discharges one, and this is not it.
 *
 * Crossing currencies takes no branch here either. The intent arrives at the write
 * boundary incomplete and is completed there, conversion legs and all, exactly as on
 * creation.
 */
interface UpdateAdvanceInvoicePaymentUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * Every identity is resolved **when the operation runs**: a transaction that matches
     * nothing is refused with `InvoiceError.NotFound`, an invoice that matches nothing
     * the same way, a paying account that matches nothing with `AccountError.NOT_FOUND`,
     * and none of them writes anything.
     *
     * @param amount how much of the invoice is being settled, in the **card's** currency.
     * @param paidAmount what leaves the account, when it is denominated differently.
     * `null` is the same-currency case.
     */
    suspend operator fun invoke(
        transactionId: Long,
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        accountId: Long,
        paidAmount: Double? = null,
    ): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the paying account. It extracts
     * the identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        transactionId: Long,
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        account: Account,
        paidAmount: Double? = null,
    ): Either<Throwable, Unit> = invoke(
        transactionId = transactionId,
        invoiceId = invoiceId,
        amount = amount,
        date = date,
        accountId = account.id,
        paidAmount = paidAmount,
    )
}
