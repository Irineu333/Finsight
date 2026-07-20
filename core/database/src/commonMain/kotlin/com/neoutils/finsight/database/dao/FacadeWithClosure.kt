package com.neoutils.finsight.database.dao

import androidx.room.Embedded
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.entity.CreditCardEntity

/**
 * A facade row plus the closure flag of its ledger account.
 *
 * Closure lives on the account and nowhere else (design D21), but a screen that
 * *renders history* needs both: the facade's name, and whether it still exists as
 * an active choice. The active listings use the filtered queries instead.
 */
data class CategoryWithArchival(
    @Embedded val category: CategoryEntity,
    val isArchived: Boolean,
)

data class CreditCardWithArchival(
    @Embedded val creditCard: CreditCardEntity,
    val isArchived: Boolean,
)
