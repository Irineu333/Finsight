package com.neoutils.finsight.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.BudgetDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.InvoiceDao
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.BudgetCategoryEntity
import com.neoutils.finsight.database.entity.BudgetEntity
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.entity.CreditCardEntity
import com.neoutils.finsight.database.entity.DimensionEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.InvoiceEntity
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.database.entity.RecurringOccurrenceEntity
import com.neoutils.finsight.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        CreditCardEntity::class,
        InvoiceEntity::class,
        AccountEntity::class,
        InstallmentEntity::class,
        BudgetEntity::class,
        BudgetCategoryEntity::class,
        RecurringEntity::class,
        RecurringOccurrenceEntity::class,
        EntryEntity::class,
        DimensionEntity::class,
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun accountDao(): AccountDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun recurringOccurrenceDao(): RecurringOccurrenceDao
    abstract fun entryDao(): EntryDao
    abstract fun dimensionDao(): DimensionDao
}

// Room compiler generates the actual implementations
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
