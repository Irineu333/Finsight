package com.neoutils.finance.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.neoutils.finance.database.dao.CategoryDao
import com.neoutils.finance.database.dao.CreditCardDao
import com.neoutils.finance.database.dao.InvoiceDao
import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.entity.CategoryEntity
import com.neoutils.finance.database.entity.CreditCardEntity
import com.neoutils.finance.database.entity.InvoiceEntity
import com.neoutils.finance.database.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, CreditCardEntity::class, InvoiceEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun invoiceDao(): InvoiceDao
}
