package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.installment_error_blocked_invoice
import com.neoutils.finsight.resources.installment_error_min_installments
import com.neoutils.finsight.resources.installment_error_missing_credit_card
import com.neoutils.finsight.resources.installment_error_missing_invoice
import com.neoutils.finsight.resources.installment_error_non_positive_count
import com.neoutils.finsight.resources.installment_error_non_positive_total
import com.neoutils.finsight.resources.installment_error_not_found
import com.neoutils.finsight.util.UiText

sealed class InstallmentError(val message: String) {

    data object MinInstallment : InstallmentError(
        message = "Installment count must be greater than 1"
    )

    data class BlockedInvoice(
        val installment: Int,
        val invoice: Invoice,
    ) : InstallmentError(
        message = "Installment $installment landed on a ${invoice.status} invoice"
    )

    data object MissingCreditCard : InstallmentError(message = "Missing target credit card")

    data object MissingInvoice: InstallmentError(message = "Missing target invoice")

    data object NotFound : InstallmentError(message = "Installment not found")

    /**
     * An installment of no shares describes nothing. The floor is one and not two,
     * unlike [MinInstallment]: an installment whose transactions were removed one by
     * one legitimately reaches a single share, and the reconciler writes exactly that.
     */
    data object NonPositiveCount : InstallmentError(
        message = "Installment count must be positive"
    )

    data object NonPositiveTotal : InstallmentError(
        message = "Installment total must be positive"
    )
}

fun InstallmentError.toUiText() = when (this) {
    InstallmentError.MinInstallment -> UiText.Res(Res.string.installment_error_min_installments)
    is InstallmentError.BlockedInvoice -> UiText.Res(Res.string.installment_error_blocked_invoice)
    InstallmentError.MissingCreditCard -> UiText.Res(Res.string.installment_error_missing_credit_card)
    InstallmentError.MissingInvoice -> UiText.Res(Res.string.installment_error_missing_invoice)
    InstallmentError.NotFound -> UiText.Res(Res.string.installment_error_not_found)
    InstallmentError.NonPositiveCount -> UiText.Res(Res.string.installment_error_non_positive_count)
    InstallmentError.NonPositiveTotal -> UiText.Res(Res.string.installment_error_non_positive_total)
}
