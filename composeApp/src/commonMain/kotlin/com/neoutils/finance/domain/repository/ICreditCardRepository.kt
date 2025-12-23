package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.CreditCard
import kotlinx.coroutines.flow.Flow

interface ICreditCardRepository {
    fun observeAllCreditCards(): Flow<List<CreditCard>>
    suspend fun getAllCreditCards(): List<CreditCard>
    suspend fun getCreditCardById(creditCardId: Long): CreditCard?
    fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?>
    suspend fun insert(creditCard: CreditCard): Long
    suspend fun update(creditCard: CreditCard)
    suspend fun delete(creditCard: CreditCard)
}
