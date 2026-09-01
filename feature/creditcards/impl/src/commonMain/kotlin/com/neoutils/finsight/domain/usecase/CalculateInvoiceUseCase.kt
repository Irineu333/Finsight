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
    /**
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
    suspend operator fun invoke(invoice: Invoice, excluding: Long? = null): Double {
        val dimensionId = invoice.dimensionId ?: return 0.0

        val owed = entryRepository
            .dimensionOwedByCurrency(dimensionId)
            .singleOrNull()
            ?.value
            ?: 0.0

        if (excluding == null) return owed

        // What that operation contributes to this figure, read exactly the way the
        // figure is read — entries are `Long` cents, debit-positive, and the owed is
        // `Double` units read credit-positive. Stated as a subtraction of the same
        // reading, the sign falls out on its own and does not depend on whether a
        // payment debits or credits.
        val contributed = entryRepository
            .getEntriesByTransaction(excluding)
            .filter { it.dimensionId == dimensionId }
            .sumOf { it.amount }
            .let { -it / 100.0 }

        return owed - contributed
    }
}
