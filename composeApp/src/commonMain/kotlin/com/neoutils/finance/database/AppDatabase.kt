package com.neoutils.finance.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.neoutils.finance.database.dao.AccountDao
import com.neoutils.finance.database.dao.CategoryDao
import com.neoutils.finance.database.dao.CreditCardDao
import com.neoutils.finance.database.dao.InvoiceDao
import com.neoutils.finance.database.dao.InstallmentDao
import com.neoutils.finance.database.dao.OperationDao
import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.entity.AccountEntity
import com.neoutils.finance.database.entity.CategoryEntity
import com.neoutils.finance.database.entity.CreditCardEntity
import com.neoutils.finance.database.entity.InvoiceEntity
import com.neoutils.finance.database.entity.InstallmentEntity
import com.neoutils.finance.database.entity.OperationEntity
import com.neoutils.finance.database.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        CreditCardEntity::class,
        InvoiceEntity::class,
        AccountEntity::class,
        InstallmentEntity::class,
        OperationEntity::class,
    ],
    version = 12,
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
}

// Room compiler generates the actual implementations
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
