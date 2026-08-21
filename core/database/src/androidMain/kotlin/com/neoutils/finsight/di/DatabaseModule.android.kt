package com.neoutils.finsight.di

import android.content.Context
import com.neoutils.finsight.database.DatabaseBuilderFactory
import com.neoutils.finsight.database.getDatabaseBuilder
import org.koin.dsl.module

actual val databasePlatformModule = module {
    single { getDatabaseBuilder(context = get()) }

    // The `Context` is taken here and held by the factory, because this module is the
    // only one that can see one — which is the entire reason a factory exists instead of
    // whoever needs a database over a path assembling the builder itself.
    single {
        val context = get<Context>()
        DatabaseBuilderFactory { path -> getDatabaseBuilder(context = context, path = path) }
    }
}
