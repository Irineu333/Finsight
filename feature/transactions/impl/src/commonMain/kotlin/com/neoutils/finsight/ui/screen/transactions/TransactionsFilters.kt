package com.neoutils.finsight.ui.screen.transactions

import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget

data class TransactionsFilters(
    /**
     * The value of the analytic axis the list is cut by — a category or the absence of
     * one — with `null` as the neutral state that cuts nothing.
     *
     * The neutral state stays *outside* the sum type: "I am not cutting" is not a value
     * of the axis (a breakdown has no "all" line), and it is a single field rather than
     * a category plus a flag so that no unreadable state (a category *and* unclassified)
     * can be spelled at all.
     */
    val subject: SpendingSubject? = null,
    val label: TransactionLabel? = null,
    val target: TransactionTarget? = null,
    val recurringOnly: Boolean = false,
    val installmentOnly: Boolean = false,
)
