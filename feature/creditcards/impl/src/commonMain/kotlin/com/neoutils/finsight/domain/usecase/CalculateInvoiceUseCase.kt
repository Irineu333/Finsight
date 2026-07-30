package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IEntryRepository

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
class CalculateInvoiceUseCase(
    private val entryRepository: IEntryRepository,
) {
    suspend operator fun invoke(invoice: Invoice): Double =
        invoice.dimensionId
            ?.let { entryRepository.dimensionOwedByCurrency(it).singleOrNull()?.value }
            ?: 0.0
}
