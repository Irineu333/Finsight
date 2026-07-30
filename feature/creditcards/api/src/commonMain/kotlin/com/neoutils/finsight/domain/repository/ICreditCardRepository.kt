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
    /**
     * @param currency what the card's `LIABILITY` account is denominated in — chosen in
     * the form and never defaulted here, because a default is how a currency gets
     * decided in silence. Along with the account form, this is one of exactly two
     * production sites where a currency is chosen at all.
     */
    suspend fun insert(creditCard: CreditCard, currency: String): Long
    suspend fun update(creditCard: CreditCard)
    suspend fun delete(creditCard: CreditCard)

    /**
     * Brings an archived card back into circulation by reopening its
     * chart-of-accounts row — the card's archival flag lives on that `LIABILITY`
     * account, not on the facade, so [accountId] is the card's `accountId`.
     */
    suspend fun unarchive(accountId: Long)

    /**
     * The currency a new card's `LIABILITY` account is **pre-selected** with.
     *
     * A card's figures are denominated by its account (design D17), and a card being
     * created has no account yet — so a form about it has no row to ask. This answers
     * "which currency is this card most likely in", and the form is free to leave it or
     * change it; what is written is whatever [insert] is given.
     */
    suspend fun currencyForNewCard(): String
}
