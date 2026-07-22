package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Recurring
import kotlinx.coroutines.flow.Flow

interface IRecurringRepository {
    fun observeAllRecurring(): Flow<List<Recurring>>
    fun observeRecurringById(id: Long): Flow<Recurring?>

    /**
     * One template by identity. A transaction carries the id of the recurring that
     * produced it and nothing more (design D6), so the screen that renders the link
     * resolves it here.
     */
    suspend fun getRecurringById(id: Long): Recurring?

    /**
     * Whether any template — active or stopped — still points at this account or
     * card. Deleting one out from under a recurring leaves it with nothing to post
     * through, so the owning feature asks before removing it.
     */
    suspend fun hasRecurringForAccount(accountId: Long): Boolean
    suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean
    suspend fun hasRecurringForCategory(categoryId: Long): Boolean
    suspend fun insert(recurring: Recurring)
    suspend fun update(recurring: Recurring)
    suspend fun delete(recurring: Recurring)
}
