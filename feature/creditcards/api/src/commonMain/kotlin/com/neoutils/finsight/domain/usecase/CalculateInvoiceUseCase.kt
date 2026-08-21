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
     */
    suspend operator fun invoke(invoices: Collection<Invoice>): Map<Long, Double>

    /** One invoice. Not another number, so not another implementation. */
    suspend operator fun invoke(invoice: Invoice): Double =
        invoke(listOf(invoice))[invoice.id] ?: 0.0
}
