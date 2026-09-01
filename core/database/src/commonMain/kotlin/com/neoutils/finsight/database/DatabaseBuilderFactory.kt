package com.neoutils.finsight.database

import androidx.room.RoomDatabase

/**
 * Builds a database over a path decided at runtime, rather than the one file this app
 * serves itself from.
 *
 * It is a type of its own because of Android and nothing else: there, opening a database
 * takes a `Context`, so the only place that can assemble the builder is the platform
 * module that already holds one. Desktop and iOS need nothing and implement it all the
 * same, which is what lets a caller ask for a database over a path without knowing which
 * platform it is standing on.
 *
 * The file is the caller's: nothing here creates, copies or removes one, and building is
 * not opening — the path comes into being, or does not, when Room is finally asked for a
 * connection.
 */
fun interface DatabaseBuilderFactory {
    operator fun invoke(path: String): RoomDatabase.Builder<AppDatabase>
}
