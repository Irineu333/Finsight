package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import kotlinx.datetime.YearMonth

sealed class TransactionsAction {

    data class SelectMonth(val yearMonth: YearMonth) : TransactionsAction()

    data class SelectScope(val scope: TransactionScope) : TransactionsAction()

    /** Selects a value of the analytic axis, or `null` for the neutral state. */
    data class SelectSubject(val subject: SpendingSubject?) : TransactionsAction()

    data class SelectLabel(val label: TransactionLabel?) : TransactionsAction()
    data class SelectTarget(val target: TransactionTarget?) : TransactionsAction()
    data class ToggleRecurring(val enabled: Boolean) : TransactionsAction()
    data class ToggleInstallment(val enabled: Boolean) : TransactionsAction()

    /**
     * Returns the list filters to neutral. Month and scope are deliberately untouched:
     * they govern the summary too, and an action announced as "clear filters" that
     * rewrote the figures above would do more than it says.
     */
    data object ClearFilters : TransactionsAction()
}
