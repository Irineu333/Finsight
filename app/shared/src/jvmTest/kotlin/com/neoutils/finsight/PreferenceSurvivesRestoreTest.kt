package com.neoutils.finsight

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.database.snapshot.replaceContentFrom
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A backup carries the archive and not the app as its owner left it** — `local-backup`
 * spec, "the backup holds the data, and the user knows it".
 *
 * The two halves of that promise fail in opposite directions and only one of them is
 * visible: an archive that does not come back is noticed immediately, while a preference
 * that *does* come back is a setting silently changed by an operation nobody expected to
 * touch it — the base currency above all, which re-expresses every consolidated figure on
 * every screen without a single row having moved.
 *
 * Today it holds by construction: the preference lives in `multiplatform-settings` and the
 * replacement only ever writes tables of the database. That is exactly why it is worth a
 * test — construction is what changes. The day a preference is moved into a table for a
 * reason that has nothing to do with backup, the restore starts carrying it and nothing
 * else in the suite says so.
 *
 * This is the only module that can ask: the base currency is kept by
 * `feature:settings:impl` and the replacement is `:core:database`, and no feature may see
 * another feature's `impl`.
 */
class PreferenceSurvivesRestoreTest {

    @Test
    fun `a restored archive leaves this install's base currency where it was`() =
        runApp(baseCurrency = "BRL") {
            val database = get<AppDatabase>()
            val baseCurrency = get<IBaseCurrencyRepository>()
            val file = File.createTempFile("finsight-backup", ".db").also { it.delete() }

            try {
                // The install that exported: one archive, under its own preference.
                account(name = "Conta corrente", currency = "BRL", isDefault = true)
                database.captureInto(
                    destinationPath = file.absolutePath,
                    appVersion = "1.0.0",
                    platform = "desktop",
                )

                // The install that restores: another preference, another archive.
                baseCurrency.set("USD")
                account(name = "Checking", currency = "USD")

                database.replaceContentFrom(file.absolutePath)

                assertEquals(
                    "USD",
                    baseCurrency.observe().value,
                    "the preference belongs to this install, and no file may move it",
                )
                assertEquals(
                    listOf("Conta corrente"),
                    accounts.getAllAccounts().map { it.name },
                    "and the archive is the file's, entirely — otherwise the first " +
                        "assertion would hold over a restore that never happened",
                )
            } finally {
                listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
            }
        }
}
