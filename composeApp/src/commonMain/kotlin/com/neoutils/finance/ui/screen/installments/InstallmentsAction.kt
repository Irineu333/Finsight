package com.neoutils.finance.ui.screen.installments

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction

sealed class InstallmentsAction {
    data class SelectInstallment(val index: Int) : InstallmentsAction()
    data class SelectCategory(val category: Category?) : InstallmentsAction()
    data class SelectType(val type: Transaction.Type?) : InstallmentsAction()
}
