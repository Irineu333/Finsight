package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.installment_error_blocked_invoice
import com.neoutils.finsight.resources.installment_error_min_installments
import com.neoutils.finsight.resources.installment_error_missing_credit_card
import com.neoutils.finsight.resources.installment_error_missing_invoice
import com.neoutils.finsight.util.UiText

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

fun InstallmentError.toUiText() = when (this) {
    InstallmentError.MinInstallment -> UiText.Res(Res.string.installment_error_min_installments)
    is InstallmentError.BlockedInvoice -> UiText.Res(Res.string.installment_error_blocked_invoice)
    InstallmentError.MissingCreditCard -> UiText.Res(Res.string.installment_error_missing_credit_card)
    InstallmentError.MissingInvoice -> UiText.Res(Res.string.installment_error_missing_invoice)
}
