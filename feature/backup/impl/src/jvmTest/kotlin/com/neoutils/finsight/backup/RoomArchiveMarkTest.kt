package com.neoutils.finsight.backup

import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The half of design D8 that is a fact about the archive rather than a rule about copies:
 * a number that rises with what is entered and does not move for what is removed.
 *
 * It runs over a real database on purpose. The whole claim is about what SQLite does to
 * `sqlite_sequence` on an insert and on a delete, and a stub asked the same questions would
 * answer whatever this test told it to.
 */
class RoomArchiveMarkTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-mark-$name", ".db")
            .also { it.delete(); temporaries += it }

    private val live = getRoomDatabase(
        builder = getDatabaseBuilder(path = temporary("live").absolutePath),
        baseCurrency = "BRL",
        currencySeeding = seeding(),
    )

    private val mark = RoomArchiveMark(live)

    @AfterTest
    fun tearDown() {
        live.close()
        temporaries.forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    private suspend fun enterTransaction(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    /**
     * SQLite creates `sqlite_sequence` the first time it issues a generated key, so on an
     * archive nobody has entered anything into there is no table to read — and the honest
     * answer to "how far has this got" is none of the way.
     */
    @Test
    fun `an archive nobody has entered anything into reads as no distance at all`() = runTest {
        assertEquals(0L, mark.current())
    }

    @Test
    fun `entering something moves the mark`() = runTest {
        val before = mark.current()

        enterTransaction("coffee")

        assertTrue(mark.current() > before, "a row was added and the mark did not notice")
    }

    /**
     * The whole reason the precondition is measured this way. The copy taken before a
     * deletion is the more complete of the two, so a deletion must leave the mark exactly
     * where the copy left it.
     */
    @Test
    fun `removing something leaves the mark where it was`() = runTest {
        val id = enterTransaction("coffee")
        val afterEntering = mark.current()

        live.transactionDao().deleteById(id)

        assertEquals(afterEntering, mark.current())
    }

    @Test
    fun `a run of removals never moves the mark`() = runTest {
        val ids = List(THREE) { enterTransaction("entry $it") }
        val afterEntering = mark.current()

        ids.forEach { live.transactionDao().deleteById(it) }

        assertEquals(
            afterEntering,
            mark.current(),
            "twenty deletions in a row must not read as twenty reasons to capture",
        )
    }

    /**
     * The mark never comes back down to a value it has already been, which is what
     * separates it from a count of rows: "remove three, enter one" leaves fewer rows than
     * before and one row no copy holds.
     */
    @Test
    fun `something entered after a removal reads as past everything before it`() = runTest {
        val first = enterTransaction("first")
        val afterFirst = mark.current()
        live.transactionDao().deleteById(first)

        enterTransaction("second")

        assertTrue(
            mark.current() > afterFirst,
            "the row entered after the deletion is not covered by the copy that preceded it",
        )
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)
        const val THREE = 3
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun seeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
