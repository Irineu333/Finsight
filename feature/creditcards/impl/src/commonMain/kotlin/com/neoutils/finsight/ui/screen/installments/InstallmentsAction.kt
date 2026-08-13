package com.neoutils.finsight.ui.screen.installments

import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionType

sealed class InstallmentsAction {
    data class SelectInstallment(val index: Int) : InstallmentsAction()
    /** Selects a value of the analytic axis, or `null` for the neutral state. */
    data class SelectSubject(val subject: SpendingSubject?) : InstallmentsAction()
    data class SelectType(val type: TransactionType?) : InstallmentsAction()
    data class SelectFilter(val filter: InstallmentFilter) : InstallmentsAction()
}
