package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import org.koin.dsl.module

/**
 * The ledger's own bindings, owned by the module that owns the ledger — the shell
 * only aggregates them, as it does for every other core.
 *
 * The DAOs and the `RoomDatabase` come from `:core:database`, which assembles the
 * real database; the ports come from whichever facade claims them, and the removal
 * prelude is the only one that may go unclaimed.
 */
val ledgerModule = module {
    single<ITransactionRepository> {
        TransactionRepository(
            database = get(),
            transactionDao = get(),
            entryDao = get(),
            accountDao = get(),
            writeGuard = get(),
            removalHook = get(),
            // Optional, unlike the two ports above: whoever wants to be told before a
            // removal registers a binding and is found here; nobody registering is a
            // valid graph, because the ledger removes correctly either way.
            removalPrelude = getOrNull() ?: TransactionRemovalPrelude.None,
            transactionMapper = get(),
            ledgerEntryWriter = get(),
        )
    }
    single<IEntryRepository> { EntryRepository(entryDao = get()) }
    factory {
        LedgerEntryWriter(
            entryDao = get(),
            accountDao = get(),
            dimensionDao = get(),
        )
    }
    factory { TransactionMapper() }
    factory { CalculateBalanceUseCase(entryRepository = get()) }
}
