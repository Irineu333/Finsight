package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.CreditCard
import kotlinx.coroutines.flow.Flow

interface ICreditCardRepository {
    fun observeAllCreditCards(): Flow<List<CreditCard>>
    suspend fun getAllCreditCards(): List<CreditCard>

    /** Every card, closed ones included — see [ICategoryRepository] for the why. */
    suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard>

    fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>>
    suspend fun getCreditCardById(creditCardId: Long): CreditCard?
    fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?>
    suspend fun insert(creditCard: CreditCard): Long
    suspend fun update(creditCard: CreditCard)
    suspend fun delete(creditCard: CreditCard)

    /**
     * Brings an archived card back into circulation by reopening its
     * chart-of-accounts row — the card's archival flag lives on that `LIABILITY`
     * account, not on the facade, so [accountId] is the card's `accountId`.
     */
    suspend fun unarchive(accountId: Long)
}
