package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection

internal fun SQLiteConnection.tableExists(tableName: String): Boolean {
    val stmt = prepare(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tableName'"
    )
    stmt.step()
    val exists = stmt.getLong(0) > 0
    stmt.close()
    return exists
}

internal fun SQLiteConnection.indexExists(indexName: String): Boolean {
    val stmt = prepare(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='$indexName'"
    )
    stmt.step()
    val exists = stmt.getLong(0) > 0
    stmt.close()
    return exists
}

internal fun SQLiteConnection.getColumns(tableName: String): List<String> {
    val stmt = prepare("PRAGMA table_info(`$tableName`)")
    val columns = mutableListOf<String>()
    while (stmt.step()) {
        columns.add(stmt.getText(1))
    }
    stmt.close()
    return columns
}
