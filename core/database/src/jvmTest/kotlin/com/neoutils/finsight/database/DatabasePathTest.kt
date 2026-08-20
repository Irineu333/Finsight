package com.neoutils.finsight.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.domain.model.DimensionKind
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The builder answers to a path. Until now the file name was welded into each
 * `Database.<platform>.kt`, which is enough while the only database the app ever opens
 * is its own — and not enough the moment a candidate file has to be opened somewhere
 * else, away from the connection that serves the app.
 *
 * Two promises are at stake here, and each has its own test: that the path given is the
 * path written to, and that the production database is not a party to any of it. The
 * default is asserted against the literal path the app has always used, deliberately
 * spelled out a second time — a default that silently moved would take the user's whole
 * ledger with it.
 */
class DatabasePathTest {

    private val file: File = File.createTempFile("finsight-path", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
    }

    private fun open(path: String) = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = testSeeding(),
    )

    private suspend fun AppDatabase.seed(name: String) {
        val dimensionId = dimensionDao().emit(DimensionKind.CATEGORY)
        categoryDao().insert(
            CategoryEntity(
                name = name,
                iconKey = "shopping",
                type = CategoryEntity.Type.EXPENSE,
                dimensionId = dimensionId,
            )
        )
    }

    private fun productionFiles() = listOf("", "-wal", "-shm").map { suffix ->
        File(defaultDatabasePath() + suffix).let {
            if (it.exists()) "${it.length()}@${it.lastModified()}" else "absent"
        }
    }

    @Test
    fun `the database is written at the path it was given`() = runBlocking {
        val database = open(file.absolutePath)
        try {
            database.seed("Groceries")
        } finally {
            database.close()
        }

        assertTrue(file.exists(), "the file at the given path was created")

        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            val statement = connection.prepare("SELECT `name` FROM `categories`")
            try {
                assertTrue(statement.step(), "the row landed in the file at the given path")
                assertEquals("Groceries", statement.getText(0))
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `the default is the path the app has always used`() {
        assertEquals(
            File(System.getProperty("user.home"), ".finance/finsight.db").absolutePath,
            defaultDatabasePath(),
            "the default path moved — every existing installation would lose its data",
        )
    }

    @Test
    fun `opening on another path leaves the production database untouched`() = runBlocking {
        val before = productionFiles()

        val database = open(file.absolutePath)
        try {
            database.seed("Groceries")
        } finally {
            database.close()
        }

        assertEquals(
            before,
            productionFiles(),
            "the production database, and its companion files, were not touched",
        )
    }
}
