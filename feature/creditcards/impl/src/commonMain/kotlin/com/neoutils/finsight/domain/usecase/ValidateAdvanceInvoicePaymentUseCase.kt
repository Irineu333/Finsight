@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.extension.today
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The invoice a valid partial payment names, resolved from the id the caller stated.
 *
 * It is handed back rather than discarded because "this invoice exists" is one of the
 * rules checked here: a caller that had to read it again would repeat the very lookup
 * whose failure this use case already named — the justification `ValidatedTransfer`
 * documents for handing back its two accounts.
 */
data class ValidatedInvoicePayment(
    val invoice: Invoice,
)

/**
 * What makes a **partial** invoice payment admissible, with a single owner.
 *
 * It is named for the operations it governs — [AdvanceInvoicePaymentUseCase] and
 * [UpdateAdvanceInvoicePaymentUseCase] — and not for payment in general, because
 * `ValidateInvoicePaymentUseCase` next to it owns the other question: whether an invoice
 * may be *discharged* on a date. Two rules, two names, and neither reachable by mistake.
 *
 * The same rules govern registering one and correcting one, so they are stated once and
 * consumed twice — the relation `TransferBetweenAccountsUseCase` and
 * `UpdateTransferUseCase` already have with `ValidateTransferUseCase`. Two copies would
 * diverge with nothing to report it.
 *
 * **The order of the guards is part of the rule.** What the invoice *accepts* is read
 * before when the payment is dated, so a refusal names the real reason: a closed invoice
 * is not refused because of *when* it is being paid but because of *what* it accepts.
 * The offer and the permission read the same predicate, so a screen that starts calling
 * this cannot inherit the permission silently.
 *
 * The [clock] is injected rather than read from the system, so this rule and the form
 * that feeds it cannot disagree about what "today" is — the form bounds its date picker
 * by the very same clock.
 *
 * Full settlement is **not** in scope: it has rules of its own — the amount that *is*
 * what is owed rather than being capped by it — and [PayInvoicePaymentUseCase] keeps
 * them.
 */
class ValidateAdvanceInvoicePaymentUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val clock: Clock,
) {
    /**
     * @param amount how much of the invoice is being settled, in the **card's** currency.
     * @param paidAmount what leaves the paying account, when it is denominated
     * differently. `null` is the same-currency case, where there is no second figure to
     * judge.
     * @param excluding the operation being rewritten, whose own contribution the ceiling
     * must leave out. `null` is a creation, where there is no such operation — and the
     * two travel through [CalculateInvoiceUseCase], so creation and correction take the
     * ceiling from the same owner.
     */
    suspend operator fun invoke(
        invoiceId: Long,
        amount: Double,
        date: LocalDate,
        paidAmount: Double? = null,
        excluding: Long? = null,
    ): Either<InvoiceError, ValidatedInvoicePayment> = either {
        ensure(amount > 0) { InvoiceError.NegativeAmount }

        ensure(paidAmount == null || paidAmount > 0) { InvoiceError.NegativeAmount }

        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) { InvoiceError.NotFound }

        ensure(invoice.acceptsPartialPayment) { InvoiceError.InvoiceNotPartiallyPayable }

        ensure(date >= invoice.openingDate && date <= invoice.closingDate) {
            InvoiceError.DateOutsideInvoicePeriod
        }

        ensure(date <= clock.today()) { InvoiceError.DateInFuture }

        val ceiling = calculateInvoiceUseCase(invoice, excluding = excluding)

        ensure(ceiling > 0.0) { InvoiceError.InvoiceNotInDebt }

        // The ceiling holds over the card's side only. The account's side is free,
        // because a limit on it would be a limit expressed in the wrong currency.
        ensure(amount <= ceiling) { InvoiceError.AmountExceedsInvoice }

        ValidatedInvoicePayment(invoice = invoice)
    }
}
