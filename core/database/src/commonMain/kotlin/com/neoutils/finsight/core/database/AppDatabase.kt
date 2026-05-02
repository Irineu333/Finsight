package com.neoutils.finsight.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.neoutils.finsight.core.database.dao.AccountDao
import com.neoutils.finsight.core.database.dao.BudgetDao
import com.neoutils.finsight.core.database.dao.CategoryDao
import com.neoutils.finsight.core.database.dao.CreditCardDao
import com.neoutils.finsight.core.database.dao.InvoiceDao
import com.neoutils.finsight.core.database.dao.InstallmentDao
import com.neoutils.finsight.core.database.dao.OperationDao
import com.neoutils.finsight.core.database.dao.RecurringDao
import com.neoutils.finsight.core.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.core.database.dao.TransactionDao
import com.neoutils.finsight.core.database.entity.AccountEntity
import com.neoutils.finsight.core.database.entity.BudgetCategoryEntity
import com.neoutils.finsight.core.database.entity.BudgetEntity
import com.neoutils.finsight.core.database.entity.CategoryEntity
import com.neoutils.finsight.core.database.entity.CreditCardEntity
import com.neoutils.finsight.core.database.entity.InvoiceEntity
import com.neoutils.finsight.core.database.entity.InstallmentEntity
import com.neoutils.finsight.core.database.entity.OperationEntity
import com.neoutils.finsight.core.database.entity.RecurringEntity
import com.neoutils.finsight.core.database.entity.RecurringOccurrenceEntity
import com.neoutils.finsight.core.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        CreditCardEntity::class,
        InvoiceEntity::class,
        AccountEntity::class,
        InstallmentEntity::class,
        OperationEntity::class,
        BudgetEntity::class,
        BudgetCategoryEntity::class,
        RecurringEntity::class,
        RecurringOccurrenceEntity::class,
    ],
    version = 7,
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
    abstract fun operationDao(): OperationDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun recurringOccurrenceDao(): RecurringOccurrenceDao
}

// Room compiler generates the actual implementations
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
