package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice

sealed class InvoicePaymentAction {

    /** Card governs invoice, invoice governs date: the top of the hierarchy. */
    data class SelectCreditCard(val creditCard: CreditCard) : InvoicePaymentAction()
    data class SelectInvoice(val invoice: Invoice) : InvoicePaymentAction()
    data class SelectAccount(val account: Account?) : InvoicePaymentAction()

    /**
     * How much of the invoice is being settled, in the **card's** currency — what the
     * archive needs, together with the date, in order to say what that costs in the
     * account's.
     */
    data class ChangeAmount(val amount: Double) : InvoicePaymentAction()

    /** The day the payment is booked, as the field holds it. */
    data class ChangeDate(val date: String) : InvoicePaymentAction()

    data class Submit(
        val amount: Double,
        /** What leaves the account, when it is denominated differently from the card. */
        val paidAmount: Double,
        // No default: the account the user picked must be carried explicitly, or an
        // omitted argument silently books the payment from the default account.
        val account: Account?,
    ) : InvoicePaymentAction()
}
