package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.feature.installments.impl.resources.Res
import com.neoutils.finsight.feature.installments.impl.resources.installment_error_blocked_invoice
import com.neoutils.finsight.feature.installments.impl.resources.installment_error_min_installments
import com.neoutils.finsight.feature.installments.impl.resources.installment_error_missing_credit_card
import com.neoutils.finsight.feature.installments.impl.resources.installment_error_missing_invoice
import com.neoutils.finsight.util.UiText

fun InstallmentError.toUiText() = when (this) {
    InstallmentError.MinInstallment -> UiText.Res(Res.string.installment_error_min_installments)
    is InstallmentError.BlockedInvoice -> UiText.Res(Res.string.installment_error_blocked_invoice)
    InstallmentError.MissingCreditCard -> UiText.Res(Res.string.installment_error_missing_credit_card)
    InstallmentError.MissingInvoice -> UiText.Res(Res.string.installment_error_missing_invoice)
}
