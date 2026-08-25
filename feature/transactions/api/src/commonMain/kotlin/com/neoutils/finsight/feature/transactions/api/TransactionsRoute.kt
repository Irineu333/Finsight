package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data class TransactionsRoute(
    val filterLabel: TransactionLabel? = null,
    val filterTarget: TransactionTarget? = null,
    /**
     * The value of the analytic axis the list opens cut by, as an **identity**.
     *
     * The id and not the category: `SpendingSubject.Categorized` wraps a whole `Category`,
     * and a `@Serializable` route carries no domain graph. Resolving it belongs to the view
     * model, which already observes the category list — including the archived ones, since
     * the list shows their history.
     *
     * An id matching no category opens the list neutral, cut by nothing.
     */
    val filterCategoryId: Long? = null,
) : NavRoute
