package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType

sealed class InstallmentsAction {
    data class SelectInstallment(val index: Int) : InstallmentsAction()
    data class SelectCategory(val category: Category?) : InstallmentsAction()
    data class SelectType(val type: TransactionType?) : InstallmentsAction()
    data class SelectFilter(val filter: InstallmentFilter) : InstallmentsAction()
}
