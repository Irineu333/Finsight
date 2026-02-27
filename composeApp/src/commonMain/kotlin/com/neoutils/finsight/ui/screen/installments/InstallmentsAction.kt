package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction

sealed class InstallmentsAction {
    data class SelectInstallment(val index: Int) : InstallmentsAction()
    data class SelectCategory(val category: Category?) : InstallmentsAction()
    data class SelectType(val type: Transaction.Type?) : InstallmentsAction()
    data class SelectFilter(val filter: InstallmentFilter) : InstallmentsAction()
}
