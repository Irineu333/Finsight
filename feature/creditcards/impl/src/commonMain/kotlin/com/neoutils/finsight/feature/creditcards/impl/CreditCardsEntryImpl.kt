package com.neoutils.finsight.feature.creditcards.impl

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.feature.creditcards.api.CreditCardsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.invoicePayment.InvoicePaymentModal
import kotlinx.datetime.LocalDate

internal class CreditCardsEntryImpl : CreditCardsEntry {
    override fun creditCardFormModal(creditCard: CreditCard?): Modal = CreditCardFormModal(creditCard)
    override fun invoicePaymentModal(invoiceId: Long?): Modal = InvoicePaymentModal(invoiceId)
    override fun editInvoicePaymentModal(transaction: Transaction): Modal =
        InvoicePaymentModal(transaction)
    override fun closeInvoiceModal(invoiceId: Long, closingDate: LocalDate): Modal =
        CloseInvoiceModal(invoiceId, closingDate)
    override fun editInvoiceBalanceModal(invoice: Invoice): Modal =
        EditInvoiceBalanceModal(invoice)
}
