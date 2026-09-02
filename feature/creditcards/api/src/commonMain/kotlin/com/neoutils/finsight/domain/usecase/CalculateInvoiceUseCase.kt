package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice

/**
 * Amount owed on an invoice = Σ the entries carrying its dimension, read positive.
 *
 * It takes the invoice, not its id: the ledger knows only the dimension, and
 * resolving facade → identity is the caller's business, which is the same
 * direction the write intent takes.
 *
 * **This is where the one-currency guarantee is written, and it is the card facade's.**
 * The ledger answers per currency for every dimension, whatever its kind — nothing in it
 * ties a dimension to a single account, and presuming otherwise would make it consult
 * `DimensionKind` on a read (design D8). What makes an invoice's figure mono-currency is
 * that this feature only ever lands an invoice's dimension on the one `LIABILITY`
 * account of its card, whose currency is immutable. So the reduction happens here,
 * beside the guarantee, and nowhere upstream.
 *
 * More than one currency on an invoice would be a broken guarantee rather than a case to
 * handle: `singleOrNull()` answering `null` collapses to zero, the same as an invoice
 * with no dimension at all.
 */
interface CalculateInvoiceUseCase {

    /**
     * The canonical form, and the one that carries the reduction: the owed of every
     * invoice given, keyed by invoice. N invoices cost one read, not N — the same
     * contract `IEntryRepository.owedByDimensionByCurrency` states, and the reason a
     * caller with a list never asks one invoice at a time (design D7).
     *
     * An invoice with no dimension, or whose dimension carries no entry, is present
     * with zero: what is asked about is the invoice, and every invoice owes something,
     * even if that something is nothing.
     *
     * @param excluding the operation whose own contribution is to be left out — the
     * ceiling a correction is judged by, since an operation that is being rewritten
     * already reduced the figure it is about to state again.
     *
     * One formula covers the three situations, without a branch between them: on a
     * creation the operation does not exist and nothing is left out; on a correction
     * over the same invoice what it settled comes back; on a correction that switched
     * invoices it has nothing there and, again, nothing is left out.
     *
     * **The default is the current owed**, which is what every read that is not a
     * correction wants. It carries a default where `contra` in `updateTransaction` and
     * `account` in `InvoicePaymentAction.Submit` deliberately do not, and the difference
     * is what the omitted value means: there it is *wrong* — an unbalanced write, the
     * default account instead of the chosen one — and here it is the ordinary, correct
     * case.
     */
    suspend operator fun invoke(
        invoices: Collection<Invoice>,
        excluding: Long? = null,
    ): Map<Long, Double>

    /** One invoice. Not another number, so not another implementation. */
    suspend operator fun invoke(invoice: Invoice, excluding: Long? = null): Double =
        invoke(listOf(invoice), excluding)[invoice.id] ?: 0.0
}
