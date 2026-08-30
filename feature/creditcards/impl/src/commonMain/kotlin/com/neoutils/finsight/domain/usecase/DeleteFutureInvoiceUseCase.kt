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

class DeleteFutureInvoiceUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
) {
    /**
     * Removes the invoice and every transaction posted to it.
     *
     * The two refusals below happen before anything is announced or destroyed, which is
     * what keeps a deletion the domain does not allow from producing a copy of an archive
     * nothing was going to be removed from.
     *
     * @param withoutCopy the person was told no copy could be kept and said to go on.
     *
     * A copy that was owed and could not be taken refuses by throwing
     * `PreventiveCaptureException` out of here, before the first row goes: the invoice and
     * all its transactions are still there, and only the person may say to come back
     * [withoutCopy].
     */
    // Withheld twice over: for every row after the first, because one removal is one
    // announcement, and for all of them when the person was offered the copy, could not
    // have it, and said to go on.
    @OptIn(WithheldAnnouncement::class)
    suspend operator fun invoke(
        invoiceId: Long,
        withoutCopy: Boolean = false,
    ): Either<InvoiceException, Unit> = either {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        ensureNotNull(invoice) {
            InvoiceException(InvoiceError.NotFound)
        }

        ensure(invoice.status.isDeletable) {
            InvoiceException(InvoiceError.CannotDeleteInvoice)
        }

        transactionRepository.observeTransactionsBy(
            dimensionId = invoice.dimensionId,
        ).first().forEachIndexed { index, transaction ->
            // Announced once, before the first row goes. A copy taken between the second
            // and the third would record the invoice already half taken apart, which is
            // the state design D6 refuses to call protection.
            transactionRepository.deleteTransactionById(
                id = transaction.id,
                announcement = if (index == 0 && !withoutCopy) {
                    RemovalAnnouncement.Announced
                } else {
                    RemovalAnnouncement.Withheld
                },
            )
        }

        invoiceRepository.deleteById(invoiceId)
    }
}
