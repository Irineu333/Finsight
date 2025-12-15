package com.neoutils.finance.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.neoutils.finance.database.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardDao {
    @Query("SELECT * FROM credit_cards ORDER BY createdAt ASC")
    fun getAllCreditCards(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards ORDER BY createdAt ASC")
    suspend fun getAllCreditCardsList(): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun getCreditCardById(id: Long): CreditCardEntity?

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    fun observeCreditCardById(id: Long): Flow<CreditCardEntity?>

    @Insert
    suspend fun insert(creditCard: CreditCardEntity): Long

    @Update
    suspend fun update(creditCard: CreditCardEntity)

    @Delete
    suspend fun delete(creditCard: CreditCardEntity)
}
