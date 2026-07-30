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

    /**
     * The currency the `LIABILITY` account of the *next* card will be born in.
     *
     * A card's figures are denominated by its account (design D17), and a card being
     * created has no account yet — so a form about it has no row to ask. This is not a
     * second answer to that question: it is the very value [insert] will write, exposed
     * so the form displays what the account is about to be, instead of guessing.
     */
    suspend fun currencyForNewCard(): String
}
