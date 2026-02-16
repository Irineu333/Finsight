package com.neoutils.finance.ui.screen.installments

import com.neoutils.finance.domain.model.Installment
import com.neoutils.finance.domain.model.Operation
import kotlinx.datetime.LocalDate

data class InstallmentsUiState(
    val installments: List<InstallmentWithOperationsUi> = emptyList(),
    val selectedInstallmentIndex: Int = 0,
) {
    val selectedInstallment: InstallmentWithOperationsUi?
        get() = installments.getOrNull(selectedInstallmentIndex)
}

data class InstallmentWithOperationsUi(
    val installment: Installment,
    val operations: List<Operation>,
    val latestOperationDate: LocalDate,
)
