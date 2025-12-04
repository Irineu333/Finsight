package com.neoutils.finance.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.neoutils.finance.data.database.CategoryDao
import com.neoutils.finance.data.database.TransactionDao
import com.neoutils.finance.data.entity.CategoryEntity
import com.neoutils.finance.data.entity.TransactionEntity

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
