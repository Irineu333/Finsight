package com.neoutils.finsight.domain.analytics.event

import com.neoutils.finsight.domain.analytics.Event

object CreateCreditCard : Event("create_credit_card")

object EditCreditCard : Event("edit_credit_card")

object DeleteCreditCard : Event("delete_credit_card")

object CloseInvoice : Event("close_invoice")

object PayInvoice : Event("pay_invoice")

object ReopenInvoice : Event("reopen_invoice")

object DeleteFutureInvoice : Event("delete_future_invoice")

object AdvanceInvoicePayment : Event("advance_invoice_payment")

object AdjustInvoiceBalance : Event("adjust_invoice_balance")
