package com.neoutils.finsight.feature.installments.screen

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.transactions.model.Transaction

sealed class InstallmentsAction {
    data class SelectInstallment(val index: Int) : InstallmentsAction()
    data class SelectCategory(val category: Category?) : InstallmentsAction()
    data class SelectType(val type: Transaction.Type?) : InstallmentsAction()
    data class SelectFilter(val filter: InstallmentFilter) : InstallmentsAction()
}
