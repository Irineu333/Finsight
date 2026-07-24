package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionInstallment

/**
 * The facade lookups a list needs to render its transactions.
 *
 * A transaction carries identities, not facades (design D6): the dimension its
 * nominal leg is classified by, and the id of the installment it belongs to. A list
 * item still shows a category icon and a "3/12" badge, so someone has to close that
 * gap — and it is the screen's view model, which owns the repositories, not the
 * ledger.
 *
 * Gathered once per list rather than per row: resolving 200 rows one at a time is
 * 200 lookups for what is one map.
 */
data class TransactionFacadeLookup(
    val categoriesByDimension: Map<Long, Category> = emptyMap(),
    val installmentsById: Map<Long, Installment> = emptyMap(),
) {
    fun categoryOf(transaction: Transaction): Category? =
        transaction.nominalDimensionId?.let { categoriesByDimension[it] }

    fun installmentLabelOf(transaction: Transaction): String? {
        val number = transaction.installmentNumber ?: return null
        val installment = transaction.installmentId?.let { installmentsById[it] } ?: return null
        return TransactionInstallment(instance = installment, number = number).label
    }

    companion object {
        val EMPTY = TransactionFacadeLookup()

        fun of(
            categories: List<Category> = emptyList(),
            installments: List<Installment> = emptyList(),
        ) = TransactionFacadeLookup(
            categoriesByDimension = categories.associateBy { it.dimensionId },
            installmentsById = installments.associateBy { it.id },
        )
    }
}
