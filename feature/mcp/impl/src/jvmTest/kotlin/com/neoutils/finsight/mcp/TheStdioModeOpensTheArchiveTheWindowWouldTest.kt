package com.neoutils.finsight.mcp

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.AppSchema
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The archive is opened the one way, and the copy that precedes a migration is taken, even when
 * the first thing to open it is an agent's call.**
 *
 * The app can be updated and then never opened: a client launches the headless mode, and the
 * pending migration runs there. Nothing about that may be a second path — the same builder, the
 * same chain of migrations, and above all the same copy of what the archive held before, because
 * that copy has exactly one chance to be taken and a migration that ran without it has already
 * spent it.
 *
 * That the two modes share the builder is the object graph's doing (design D5), and it is stated
 * once, in `databasePlatformModule`. What is asked here is the half a graph cannot state: that the
 * opening is *lazy* — nothing of the session touches the archive until a call does — so the
 * migration and its copy happen inside the first call and before its answer, rather than at some
 * earlier moment that would have to be arranged.
 *
 * **The fixture is this build's own archive, left declaring the version before this one**, which is
 * exactly what an app that was updated finds. It carries no frozen schema of its own on purpose: a
 * hand-written fixture proves itself and not the migration. It does rest on the last hop being
 * re-runnable over an archive already shaped for it — if a future migration is not, this fixture is
 * where to say so.
 *
 * The preferences and the activity log are the harness's own, on an archive of its own. What is
 * being asked about is the archive the *call* opens, and keeping the plumbing out of it is what
 * makes "nothing had opened it yet" a fact rather than an arrangement.
 */
class TheStdioModeOpensTheArchiveTheWindowWouldTest {

    private val folder: File = File.createTempFile("finsight-stdio-migration", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    private val archive = File(folder, "finsight.db").absolutePath

    private val copy = File(folder, "premigration.db").absolutePath

    @AfterTest
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun `the first call migrates the archive and leaves the copy that precedes it`() = runBlocking {
        archiveOnThePreviousVersion()

        // What the graph does when something first asks for the database, and nothing else does:
        // the builder is assembled — which is when the copy is taken — and Room runs the chain on
        // the first query.
        val opened = lazy {
            getRoomDatabase(
                builder = getDatabaseBuilder(path = archive, captureInto = copy),
                baseCurrency = BASE_CURRENCY,
                currencySeeding = seeding,
            )
        }

        val tool = SpyTool(
            name = McpToolName.LIST_ACCOUNTS.wireName,
            effect = McpToolEffect.READS,
            answer = {
                McpToolResult(
                    text = opened.value.accountDao().getAllLedgerAccounts().joinToString { it.name },
                )
            },
        )

        McpServerHarness(tools = listOf(tool)).use { harness ->
            harness.serverSettings.setEnabled(true)

            assertFalse(
                File(copy).exists(),
                "something opened the archive before the client had asked anything of it",
            )

            harness.stdioSession().servedOverStdio { client ->
                assertEquals(
                    "Checking",
                    (client.callTool(tool.name, emptyMap()).content.single() as TextContent).text,
                    "The call did not answer from the archive it was supposed to open.",
                )
            }

            if (opened.isInitialized()) opened.value.close()
        }

        assertTrue(File(copy).exists(), "no copy was taken before the migration ran")
        assertEquals(
            (AppSchema.VERSION - 1).toLong(),
            onFile(copy) { it.version() },
            "the copy is not of the archive as it was, before the migration touched it",
        )
        assertEquals(
            "Checking",
            onFile(copy) { it.scalarText("SELECT `name` FROM `accounts` LIMIT 1") },
            "what the user had did not travel with the copy",
        )
        assertEquals(
            AppSchema.VERSION.toLong(),
            onFile(archive) { it.version() },
            "the archive itself was not migrated",
        )
    }

    /**
     * The archive an updated app finds: written by this build, so the schema is the real one, and
     * left declaring the version before this one, so opening it migrates.
     */
    private fun archiveOnThePreviousVersion() = runBlocking {
        val database = getRoomDatabase(
            builder = getDatabaseBuilder(path = archive),
            baseCurrency = BASE_CURRENCY,
            currencySeeding = seeding,
        )
        database.accountDao().insert(AccountEntity(name = "Checking", currency = BASE_CURRENCY))
        database.close()

        onFile(archive) { it.execSQL("PRAGMA user_version = ${AppSchema.VERSION - 1}") }
    }

    /**
     * The seeding with the device taken out of it, so the rows are the test's and not the locale's
     * of whichever machine runs the suite.
     */
    private val seeding = object : CurrencySeeding {
        override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
        override fun symbolOf(code: String): String = code
    }

    private fun <T> onFile(path: String, block: (SQLiteConnection) -> T): T {
        val connection = BundledSQLiteDriver().open(path)
        return try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    private fun SQLiteConnection.version(): Long {
        val statement = prepare("PRAGMA user_version")
        try {
            statement.step()
            return statement.getLong(0)
        } finally {
            statement.close()
        }
    }

    private fun SQLiteConnection.scalarText(sql: String): String {
        val statement = prepare(sql)
        try {
            statement.step()
            return statement.getText(0)
        } finally {
            statement.close()
        }
    }

    private companion object {
        const val BASE_CURRENCY = "BRL"
    }
}
