package com.neoutils.finsight.di

import android.content.Context
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
            context = get(),
            captureInto = getOrNull<PreMigrationCopyTarget>()?.path(),
        )
        getOrNull<PreMigrationCopyTarget>()?.settle()
        builder
    }

    // The `Context` is taken here and held by the factory, because this module is the
    // only one that can see one — which is the entire reason a factory exists instead of
    // whoever needs a database over a path assembling the builder itself.
    single {
        val context = get<Context>()
        DatabaseBuilderFactory { path -> getDatabaseBuilder(context = context, path = path) }
    }
}
