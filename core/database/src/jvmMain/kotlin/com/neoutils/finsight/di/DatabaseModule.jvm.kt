package com.neoutils.finsight.di

import com.neoutils.finsight.database.DatabaseBuilderFactory
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.snapshot.PreMigrationCopyTarget
import org.koin.dsl.module

actual val databasePlatformModule = module {
    // Assembling the database is where the copy taken before migrating is decided, and
    // the decision is only ever passed through: the target answers a path or nothing, and
    // that answer *is* the whole condition (design D11). Nothing here reads a preference,
    // and an unclaimed port is a build that takes no such copy.
    single {
        getDatabaseBuilder(captureInto = getOrNull<PreMigrationCopyTarget>()?.path())
    }
    single { DatabaseBuilderFactory { path -> getDatabaseBuilder(path) } }
}
