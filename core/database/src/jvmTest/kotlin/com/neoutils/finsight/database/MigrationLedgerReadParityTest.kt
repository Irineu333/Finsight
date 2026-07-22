package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Closes the parity gap the other migration tests leave open: they assert the
 * migrated rows with their own raw SQL, and the query tests run the production
 * DAOs over a hand-built ledger — the two halves never meet. Here the fixture is
 * a real v7 database, Room runs [MIGRATION_7_9], and the figures the app actually
 * shows are read back through the **production** `EntryDao` queries and compared
 * to the v7 legacy values. A silent sign flip or off-by-one between how the
 * migration writes and how a screen reads would surface here, not on a device.
 */
class MigrationLedgerReadParityTest {

    private val file: File = File.createTempFile("finsight-read-parity", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `given a v7 ledger when read through production queries after migration then figures match`() = runTest {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            buildV7Fixture(connection)
            connection.execSQL("PRAGMA user_version = 7")
        }

        val database = openMigrated()

        val entryDao = database.entryDao()
        val accounts = database.accountDao().getAllLedgerAccounts()
        val aId = accounts.first { it.name == "A" }.id
        val foodDimensionId = database.categoryDao().getAllCategories().first { it.name == "Food" }.dimensionId

        // Account balance: A = -50 (expense) - 100 (transfer out) + 30 (adjustment) = -120.00.
        assertEquals(-12000L, entryDao.balanceOf(aId))

        // Net worth (ASSET + LIABILITY): A(-12000) + B(+10000) + C(-4000) + card(-6000);
        // the two reconstructed closed accounts are zeroed by their write-off.
        assertEquals(-12000L, entryDao.netWorthCents())

        // Invoice owed, natural: purchase -10000 + payment +4000 = -6000 (60.00 owed).
        // Read through the invoice's dimension, which is what carries it since v10.
        val invoiceDimensionId = database.invoiceDao().getAllInvoices().first { it.id == 1L }.dimensionId!!
        assertEquals(-6000L, entryDao.dimensionNaturalBalance(invoiceDimensionId))

        // Category total, all-time: op1 (5000) + op6 (2000) + op7 (1500) = 8500.
        // Read through the category's dimension, which is what carries it since v10.
        assertEquals(8500L, entryDao.dimensionNaturalBalance(foodDimensionId))

        // Temporal cut, read through the production query: nothing before A's first
        // movement, everything by the month of its last.
        assertEquals(0L, entryDao.balanceUpToMonth(aId, "2023-12"))
        assertEquals(-12000L, entryDao.balanceUpToMonth(aId, "2024-01"))

        database.close()
    }

    /**
     * The parity gate of v10, figure by figure and keyed by facade id.
     *
     * The "before" is raw SQL over the v9 schema, taken while `entries.invoiceId`
     * still exists; the "after" is the production reads over v10, where the same
     * invoice total comes from a dimension. Only the mechanism changed, so only the
     * production side of the comparison moved — and the two must still agree on
     * every account balance, every invoice owed, every category total and net worth.
     */
    @Test
    fun `given a v9 ledger when v10 rewrites the mechanism then every figure is unchanged`() = runTest {
        val expected = BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            buildV7Fixture(connection)
            // v9 by hand, so the snapshot can be taken before v10 removes the columns
            // it is computed from.
            MIGRATION_7_9.migrate(connection)
            connection.execSQL("PRAGMA user_version = 9")
            connection.verifyLedgerBalanced(stage = "v9 snapshot")
            connection.readV9Figures()
        }

        val database = openMigrated()
        assertEquals(expected, database.readProductionFigures())
        database.close()
    }

    private fun openMigrated(): AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .addMigrations(MIGRATION_7_9, MIGRATION_9_10)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

/**
 * The same representative v7 data as `Migration7To9Test`, including the orphan
 * legs of a deleted account and card, so the production reads exercise the
 * closed-account reconstruction too.
 */
private fun buildV7Fixture(connection: SQLiteConnection) {
    V7_SCHEMA.forEach(connection::execSQL)

    connection.execSQL("INSERT INTO `accounts` (`id`,`name`,`iconKey`,`isDefault`,`createdAt`) VALUES (1,'A','wallet',1,1000),(2,'B','wallet',0,1000)")
    connection.execSQL("INSERT INTO `categories` (`id`,`name`,`iconKey`,`type`,`createdAt`) VALUES (1,'Food','food','EXPENSE',1000)")

    // op1: expense 50 from A, category Food (single leg)
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (1,'TRANSACTION','2024-01-10')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) VALUES (1,'EXPENSE',50.0,'2024-01-10',1,'ACCOUNT',1)")

    // op2: transfer 100 A->B (two legs, already balanced)
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (2,'TRANSFER','2024-01-11')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) VALUES (2,'EXPENSE',100.0,'2024-01-11','ACCOUNT',1)")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) VALUES (2,'INCOME',100.0,'2024-01-11','ACCOUNT',2)")

    // op3: adjustment +30 on A (single leg)
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (3,'TRANSACTION','2024-01-12')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) VALUES (3,'ADJUSTMENT',30.0,'2024-01-12','ACCOUNT',1)")

    // Card-payment scenario on account C(3): purchase 100 then pay 40 (invoice 1).
    connection.execSQL("INSERT INTO `accounts` (`id`,`name`,`iconKey`,`isDefault`,`createdAt`) VALUES (3,'C','wallet',0,1000)")
    connection.execSQL("INSERT INTO `credit_cards` (`id`,`name`,`limit`,`closingDay`,`dueDay`,`iconKey`,`createdAt`) VALUES (1,'Card',1000.0,10,20,'card',1000)")
    connection.execSQL(
        "INSERT INTO `invoices` (`id`,`creditCardId`,`openingMonth`,`closingMonth`,`dueMonth`,`status`,`createdAt`) " +
            "VALUES (1,1,'2024-01','2024-02','2024-02','OPEN',1000)"
    )
    // op4: card purchase 100 (single card leg)
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (4,'TRANSACTION','2024-02-01')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`creditCardId`,`invoiceId`) VALUES (4,'EXPENSE',100.0,'2024-02-01','CREDIT_CARD',1,1)")
    // op5: payment 40 — account leg (also carries the card ref) + card leg
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (5,'PAYMENT','2024-02-05')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`,`creditCardId`,`invoiceId`) VALUES (5,'EXPENSE',40.0,'2024-02-05','ACCOUNT',3,1,1)")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`creditCardId`,`invoiceId`) VALUES (5,'INCOME',40.0,'2024-02-05','CREDIT_CARD',1,1)")

    // Orphaned legs from a deleted account/card (FK SET_NULL).
    // op6: expense 20 whose account was deleted (accountId NULL), category Food.
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (6,'TRANSACTION','2024-03-01')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) VALUES (6,'EXPENSE',20.0,'2024-03-01',1,'ACCOUNT',NULL)")
    // op7: card purchase 15 whose card was deleted (creditCardId NULL), category Food.
    connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (7,'TRANSACTION','2024-03-02')")
    connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`creditCardId`) VALUES (7,'EXPENSE',15.0,'2024-03-02',1,'CREDIT_CARD',NULL)")
}
