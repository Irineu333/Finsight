package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finsight.database.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

// Same rule as categories: a card is closed when its LIABILITY account is (D21).
private const val OPEN_CREDIT_CARDS =
    "SELECT cc.* FROM credit_cards cc JOIN accounts a ON a.id = cc.accountId " +
        "WHERE a.isArchived = 0"

// Same as categories: history keeps rendering a card that was later closed.
private const val ALL_CREDIT_CARDS =
    "SELECT cc.*, a.isArchived AS isArchived FROM credit_cards cc JOIN accounts a ON a.id = cc.accountId"

@Dao
interface CreditCardDao {
    @Query(ALL_CREDIT_CARDS + " ORDER BY cc.createdAt ASC")
    suspend fun getAllCreditCardsIncludingClosed(): List<CreditCardWithArchival>

    @Query(ALL_CREDIT_CARDS + " ORDER BY cc.createdAt ASC")
    fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCardWithArchival>>

    @Query(OPEN_CREDIT_CARDS + " ORDER BY cc.createdAt ASC")
    fun observeAllCreditCards(): Flow<List<CreditCardEntity>>

    @Query(OPEN_CREDIT_CARDS + " ORDER BY cc.createdAt ASC")
    suspend fun getAllCreditCardsList(): List<CreditCardEntity>

    // Carries the account's archival, like the observe variant: a plain read would
    // report every card as active, so a caller checking `isArchived` off a by-id read
    // would always be told "false".
    @Query(ALL_CREDIT_CARDS + " WHERE cc.id = :creditCardId")
    suspend fun getCreditCardWithArchivalById(creditCardId: Long): CreditCardWithArchival?

    @Query(ALL_CREDIT_CARDS + " WHERE cc.id = :creditCardId")
    fun observeCreditCardWithArchivalById(creditCardId: Long): Flow<CreditCardWithArchival?>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    fun observeCreditCardById(id: Long): Flow<CreditCardEntity?>

    @Insert
    suspend fun insert(creditCard: CreditCardEntity): Long

    @Update
    suspend fun update(creditCard: CreditCardEntity)

    @Delete
    suspend fun delete(creditCard: CreditCardEntity)
}
