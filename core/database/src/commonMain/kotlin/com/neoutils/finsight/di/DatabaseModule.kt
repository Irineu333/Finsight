package com.neoutils.finsight.di

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.BudgetDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.EntryDao
import com.neoutils.finsight.database.dao.ExchangeRateDao
import com.neoutils.finsight.database.dao.InvoiceDao
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.domain.model.legacyRelabelCurrency
import androidx.room.RoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    // Bound under both types, and deliberately the **same instance**: `:core:ledger`
    // takes the `RoomDatabase` supertype, because opening a write transaction is a
    // Room capability and the ledger has no business knowing which schema it is part
    // of. A second instance here would not just waste a connection — the removal hook
    // runs inside the ledger's write transaction and opens the facade's, and two
    // pools would deadlock instead of nesting.
    //
    // The relabel target is resolved *outside* this module and arrives as a plain
    // code: the migration needs a currency, not a locale and not a catalog. It is
    // recomputed on every start and that is harmless — the migration it feeds runs
    // once, and `user_version` is what records that it did.
    single<AppDatabase> {
        getRoomDatabase(builder = get(), relabelCurrency = legacyRelabelCurrency())
    } bind RoomDatabase::class
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<CreditCardDao> { get<AppDatabase>().creditCardDao() }
    single<InvoiceDao> { get<AppDatabase>().invoiceDao() }
    single<AccountDao> { get<AppDatabase>().accountDao() }
    single<InstallmentDao> { get<AppDatabase>().installmentDao() }
    single<BudgetDao> { get<AppDatabase>().budgetDao() }
    single<RecurringDao> { get<AppDatabase>().recurringDao() }
    single<RecurringOccurrenceDao> { get<AppDatabase>().recurringOccurrenceDao() }
    single<EntryDao> { get<AppDatabase>().entryDao() }
    single<DimensionDao> { get<AppDatabase>().dimensionDao() }
    single<ExchangeRateDao> { get<AppDatabase>().exchangeRateDao() }
}

expect val databasePlatformModule: Module
