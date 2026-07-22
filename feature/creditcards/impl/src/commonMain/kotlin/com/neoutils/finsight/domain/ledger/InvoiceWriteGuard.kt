package com.neoutils.finsight.domain.ledger

import com.neoutils.finsight.domain.error.InvoiceLockedException
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.repository.IInvoiceRepository

/**
 * The invoice-status invariant, plugged into the ledger's single write boundary.
 *
 *  - `PAID` — settled history: nothing may touch it.
 *  - `CLOSED` — takes no new spending, but must still accept the payment that
 *    settles it, which is the whole point of closing.
 *  - anything else — free.
 *
 * `CLOSED` and `PAID` behave differently here, which is why `isClosedToNewExpenses`
 * (which fuses them) is not the predicate: it happens to be right only for creating
 * an expense, where the two coincide.
 *
 * It lives here, with invoices, and reaches the ledger through
 * [DimensionWriteGuard] — the ledger knows a set of dimensions was touched and that
 * someone may refuse, never that an invoice exists (design D11). Keeping it at the
 * boundary rather than in the screens that offer the action is what stops two
 * screens disagreeing about what is editable; that divergence is why
 * `InvoiceWriteGuardTest` exists.
 */
class InvoiceWriteGuard(
    private val invoiceRepository: IInvoiceRepository,
) : DimensionWriteGuard {

    override suspend fun ensureAccepts(write: LedgerWrite) {
        val invoices = invoiceRepository.getAllInvoices()
            .filter { it.dimensionId in write.dimensionIds }

        invoices.forEach { invoice ->
            when {
                invoice.status.isPaid -> throw InvoiceLockedException(LedgerError.PaidInvoice)
                invoice.status.isClosed && !write.settlesALiability ->
                    throw InvoiceLockedException(LedgerError.ClosedInvoice)
            }
        }
    }
}
