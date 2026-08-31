package com.neoutils.finsight.di

import com.neoutils.finsight.database.DatabaseBuilderFactory
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.snapshot.PreMigrationCopyTarget
import org.koin.dsl.module

actual val databasePlatformModule = module {
    // Assembling the database is where the copy taken before migrating is decided, and
    // the decision is only ever passed through: the target answers a path or nothing, and
    // that answer *is* the whole condition (design D11). Nothing here reads a preference,
    // and an unclaimed port is a build that takes no such copy. Settling comes after the
    // builder, because that is when the capture behind the answer has been tried — the port
    // is claimed as a singleton, so both calls reach the object that answered.
    single {
        val builder = getDatabaseBuilder(
            captureInto = getOrNull<PreMigrationCopyTarget>()?.path(),
        )
        getOrNull<PreMigrationCopyTarget>()?.settle()
        builder
    }
    single { DatabaseBuilderFactory { path -> getDatabaseBuilder(path) } }
}
