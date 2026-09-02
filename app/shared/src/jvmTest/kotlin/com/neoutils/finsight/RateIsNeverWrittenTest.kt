package com.neoutils.finsight

import com.neoutils.finsight.database.AppSchema
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A rate is never an input to a write, and never a persisted figure.**
 *
 * Two sentences the change rests on, both stated as inspections because both are
 * properties of *what exists* rather than of what runs:
 *
 * - the write vocabulary carries no rate (`balanced-ledger`). A caller of a
 *   cross-currency operation states the two amounts the statement shows, and the rate
 *   is derived from the legs afterwards — the same treatment the label and the display
 *   sign already get (design D6). A rate parameter anywhere on the way in would make
 *   the boundary able to invent a leg the user never stated;
 * - nothing stored holds a value converted to the base (`currency-consolidation`). A
 *   converted column would be a second invariant that exchange rounding breaks by
 *   construction, would freeze a rate inside the ledger, and would turn changing the
 *   base currency into a data migration — the `native_amount` of Firefly III, repaired
 *   in bulk by a fix-up command (design D1).
 */
class RateIsNeverWrittenTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun File.relativePath() = relativeTo(repoRoot).invariantSeparatorsPath

    @Test
    fun `no intent, leg or contra leg carries a rate`() {
        val vocabulary = File(
            repoRoot,
            "core/ledger/src/commonMain/kotlin/com/neoutils/finsight/domain/model/TransactionIntent.kt",
        ).readText()

        // Every property and parameter declared in the file, whatever its position.
        val declarations = Regex("""\bval\s+(\w+)\s*:|\b(\w+)\s*:\s*\w""")
            .findAll(vocabulary)
            .mapNotNull { it.groupValues[1].ifEmpty { it.groupValues[2] }.ifEmpty { null } }
            .toSet()

        val rateShaped = declarations.filter { Regex("rate|exchange|taxa", RegexOption.IGNORE_CASE) in it }

        assertEquals(
            emptyList(),
            rateShaped,
            "The write vocabulary gained something rate-shaped. A cross-currency " +
                "intent states its two amounts and the boundary derives the rate from " +
                "the legs it wrote; a rate on the way in is a number nobody can check " +
                "against the statement.",
        )
    }

    @Test
    fun `the write boundary is never handed a rate`() {
        val boundary = repoRoot.resolve("core/ledger/src/commonMain/kotlin/com/neoutils/finsight")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val namesARate = Regex("""ExchangeRate|\brate\s*:""")
        val found = boundary.filter { namesARate.containsMatchIn(it.readText()) }.map { it.relativePath() }

        assertEquals(
            emptyList(),
            found,
            "The ledger named a rate. It does not know what one is: `Σ = 0` per " +
                "currency needs no rate, and the figure that does is consolidated " +
                "above it.",
        )
    }

    @Test
    fun `no column of the schema holds a value converted to the base`() {
        // The current schema, whichever version that is. Naming a version here would
        // make this test pass by going stale: the claim is about the shape the app
        // ships, and a new migration must not quietly stop being covered by it.
        val schemas = File(
            repoRoot,
            "core/database/schemas/com.neoutils.finsight.database.AppDatabase",
        ).listFiles { file -> file.extension == "json" }.orEmpty()

        val latest = checkNotNull(schemas.maxByOrNull { it.nameWithoutExtension.toInt() }) {
            "no exported schema to read"
        }

        // Reading the newest export is only "the shape the app ships" while the export
        // keeps up with the chain. Bumping the version without regenerating would leave
        // this reading the previous schema and passing — going stale by the other route.
        assertEquals(
            AppSchema.VERSION,
            latest.nameWithoutExtension.toInt(),
            "The newest exported schema is not the version the app declares. Regenerate " +
                "the schema, or this test covers a shape the app no longer ships.",
        )

        val schema = latest.readText()

        val columnNames = Regex(""""fieldPath"\s*:\s*"([^"]+)"""")
            .findAll(schema)
            .map { it.groupValues[1] }
            .toSet()

        // The names such a column takes in every app that has one: Firefly III's
        // `native_amount`, and the `amountBase`/`convertedAmount` of the consumer apps.
        val converted = columnNames.filter {
            Regex("base(Amount|Value)|amountBase|native|converted", RegexOption.IGNORE_CASE) in it
        }

        assertEquals(
            emptyList(),
            converted,
            "A column now holds a converted value. Conversion is a read-time choice " +
                "of presentation: persisting it freezes a rate, and every figure of " +
                "the past would move when the rate is corrected.",
        )

        // The exchange rate table itself is the one place a rate is stored, and it
        // stores the rate — never money expressed through it.
        assertTrue(
            """"tableName": "exchange_rates"""" in schema,
            "The rate archive is gone from the schema; the consolidation has nothing " +
                "to read.\n",
        )
    }
}
