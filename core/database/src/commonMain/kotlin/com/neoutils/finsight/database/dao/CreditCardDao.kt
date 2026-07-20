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
    "SELECT cc.* FROM credit_cards cc LEFT JOIN accounts a ON a.id = cc.accountId " +
        "WHERE COALESCE(a.isClosed, 0) = 0"

@Dao
interface CreditCardDao {
    @Query(OPEN_CREDIT_CARDS + " ORDER BY cc.createdAt ASC")
    fun observeAllCreditCards(): Flow<List<CreditCardEntity>>

    @Query(OPEN_CREDIT_CARDS + " ORDER BY cc.createdAt ASC")
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
