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
 * The ledger answers per currency, because nothing there binds a dimension to one account.
 * That an invoice's dimension only ever lands on its own card's LIABILITY account is this
 * facade's guarantee — including when the payment crosses currencies, since the money that
 * leaves the paying account is a leg of its own and the exchange residue carries no
 * dimension at all. So the reduction happens **here**, where the guarantee is kept.
 */
class CalculateInvoiceUseCase(
    private val entryRepository: IEntryRepository,
) {
    suspend operator fun invoke(invoice: Invoice): Double =
        invoice.dimensionId?.let { entryRepository.dimensionOwed(it).soleAmount } ?: 0.0
}
