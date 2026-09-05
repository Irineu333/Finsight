package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
import com.neoutils.finsight.domain.ledger.WithheldAnnouncement
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.first

class DeleteFutureInvoiceUseCaseImpl(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
) : DeleteFutureInvoiceUseCase {

    // Withheld only where the person was offered the copy, could not have it, and said to
    // go on. Once for the batch is what the batch call already gives.
    @OptIn(WithheldAnnouncement::class)
    override suspend fun invoke(
        invoiceId: Long,
        withoutCopy: Boolean,
    ): Either<InvoiceException, Unit> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status.isDeletable) {
            InvoiceException(InvoiceError.CannotDeleteInvoice)
        }

        val posted = transactionRepository.observeTransactionsBy(
            dimensionId = invoice.dimensionId,
        ).first()

        // The batch is one removal and the copy owed before it is one copy, said once —
        // which is the whole reason this is the batch call and not a row at a time. Said
        // per row it rode on the *first* row, so an invoice carrying none said it never:
        // the sheet promised a copy that this then did not take, and the invoice went
        // anyway. Announcing here happens before the list is even looked at, so an invoice
        // with nothing posted to it keeps the promise its confirmation made.
        //
        // Which deletions are worth a copy is the action's class and not the row count
        // (design D7). An invoice with nothing on it is still a DELETE_INVOICE, and design
        // D8 is what keeps that cheap: with nothing added since the last copy, the copy
        // owed is the one already in the destination and no file is written.
        transactionRepository.deleteTransactionsByIds(
            ids = posted.map { it.id },
            announcement = if (withoutCopy) {
                RemovalAnnouncement.Withheld
            } else {
                RemovalAnnouncement.Announced
            },
        )

        invoiceRepository.deleteById(invoiceId)
    }
}
