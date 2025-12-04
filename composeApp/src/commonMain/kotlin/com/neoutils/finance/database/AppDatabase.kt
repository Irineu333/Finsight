package com.neoutils.finance.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.neoutils.finance.database.dao.CategoryDao
import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.entity.CategoryEntity
import com.neoutils.finance.database.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
}
