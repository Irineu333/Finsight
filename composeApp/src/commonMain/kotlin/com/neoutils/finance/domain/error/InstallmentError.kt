package com.neoutils.finance.domain.error

import com.neoutils.finance.domain.model.Invoice

sealed class InstallmentError(val message: String) {

    data object MinInstallment : InstallmentError(
        message = "O número de parcelas deve ser superior a 1"
    )

    data class BlockedInvoice(
        val installment: Int,
        val invoice: Invoice,
    ) : InstallmentError(
        message = "Parcela $installment coincidiu com uma fatura ${invoice.status}"
    )

    data object MissingCreditCard : InstallmentError(message = "Missing target credit card")

    data object MissingInvoice: InstallmentError(message = "Missing target invoice")
}