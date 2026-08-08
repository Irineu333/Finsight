package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration7To10
import com.neoutils.finsight.database.migration.verifyLedgerBalanced
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The single migration a real device runs: v7 — the last schema that shipped — to
 * v10, the double-entry ledger. The fixture is built from [V7_SCHEMA], which is the
 * exported `7.json` verbatim; a fixture that is not the real old schema proves
 * nothing about the real migration.
 *
 * What it must preserve is every figure the app renders — each account's balance,
 * what an invoice owes, what a category totals, net worth — while replacing the
 * mechanism that produces them. What it must produce is a ledger where `Σ = 0` per
 * transaction, no leg without an account, and no dimension without a row.
 */
class Migration7To10Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        V7_SCHEMA.forEach(connection::execSQL)

        // Accounts A(1), B(2); categories Food(1, EXPENSE) and Salary(2, INCOME).
        connection.execSQL("INSERT INTO `accounts` (`id`,`name`,`iconKey`,`isDefault`,`createdAt`) VALUES (1,'A','wallet',1,1000),(2,'B','wallet',0,1000)")
        connection.execSQL(
            "INSERT INTO `categories` (`id`,`name`,`iconKey`,`type`,`createdAt`) VALUES " +
                "(1,'Food','food','EXPENSE',1000),(2,'Salary','salary','INCOME',1000)"
        )

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
        // op5: payment 40 — account leg (also carries the card ref, like the real use case) + card leg
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (5,'PAYMENT','2024-02-05')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`,`creditCardId`,`invoiceId`) VALUES (5,'EXPENSE',40.0,'2024-02-05','ACCOUNT',3,1,1)")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`creditCardId`,`invoiceId`) VALUES (5,'INCOME',40.0,'2024-02-05','CREDIT_CARD',1,1)")

        // Orphaned legs from deleted account/card (FK SET_NULL): accountId/creditCardId is NULL.
        // op6: expense 20 whose account was deleted (target ACCOUNT, accountId NULL), category Food.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (6,'TRANSACTION','2024-03-01')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) VALUES (6,'EXPENSE',20.0,'2024-03-01',1,'ACCOUNT',NULL)")
        // op7: card purchase 15 whose card was deleted (target CREDIT_CARD, creditCardId NULL), category Food.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (7,'TRANSACTION','2024-03-02')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`creditCardId`) VALUES (7,'EXPENSE',15.0,'2024-03-02',1,'CREDIT_CARD',NULL)")

        // op8: income 900 into A, category Salary — the INCOME side of the chart.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (8,'TRANSACTION','2024-01-05')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) VALUES (8,'INCOME',900.0,'2024-01-05',2,'ACCOUNT',1)")
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    private fun scalar(sql: String): Long {
        val stmt = connection.prepare(sql)
        stmt.step()
        val value = stmt.getLong(0)
        stmt.close()
        return value
    }

    private fun text(sql: String): String? {
        val stmt = connection.prepare(sql)
        val value = if (stmt.step()) stmt.getText(0) else null
        stmt.close()
        return value
    }

    /** Σ of the whole ledger, which must be zero for every currency at all times. */
    private fun wholeLedgerSum(): Long = scalar("SELECT COALESCE(SUM(amount),0) FROM entries")

    private fun accountId(name: String): Long = scalar("SELECT `id` FROM `accounts` WHERE `name` = '$name'")

    private fun dimensionOf(categoryId: Long): Long =
        scalar("SELECT `dimensionId` FROM `categories` WHERE `id` = $categoryId")

    // --- Shape -------------------------------------------------------------

    @Test
    fun `given v7 when migrated then the schema is the v10 one`() {
        Migration7To10.migrate(connection)

        assertTrue(connection.tableExists("entries"))
        assertTrue(connection.tableExists("dimensions"))
        // The legacy aggregate table is gone and the aggregate took the leg table's name.
        assertFalse(connection.tableExists("operations"))
        assertTrue("kind" !in connection.getColumns("transactions"))
        assertTrue("categoryId" !in connection.getColumns("transactions"))
        assertTrue("target" !in connection.getColumns("transactions"))

        // Entries carry a dimension, never an invoice id.
        assertTrue("dimensionId" in connection.getColumns("entries"))
        assertFalse("invoiceId" in connection.getColumns("entries"))

        val accounts = connection.getColumns("accounts")
        assertTrue("type" in accounts)
        assertTrue("currency" in accounts)
        assertTrue("isArchived" in accounts)
    }

    @Test
    fun `given v7 when migrated then entries indices exist and are attached to entries`() {
        Migration7To10.migrate(connection)

        assertTrue(connection.indexExists("index_entries_transactionId"))
        assertTrue(connection.indexExists("index_entries_accountId"))
        assertTrue(connection.indexExists("index_entries_dimensionId"))
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM pragma_index_list('entries') WHERE name = 'index_entries_transactionId'"),
        )
        // The v9 index on the column that no longer exists must not survive by name.
        assertFalse(connection.indexExists("index_entries_invoiceId"))
    }

    @Test
    fun `given v7 when migrated then entries point at transactions and dimensions`() {
        Migration7To10.migrate(connection)

        assertEquals(
            "transactions",
            text("SELECT \"table\" FROM pragma_foreign_key_list('entries') WHERE \"from\" = 'transactionId'"),
        )
        assertEquals(
            "dimensions",
            text("SELECT \"table\" FROM pragma_foreign_key_list('entries') WHERE \"from\" = 'dimensionId'"),
        )
    }

    @Test
    fun `given v7 when migrated then every facade carries the NOT NULL link it needs`() {
        Migration7To10.migrate(connection)

        // A card is a facade over a chart row; a category is a facade over a dimension.
        // Neither may exist without it, so no reader has to handle the absence.
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM pragma_table_info('credit_cards') WHERE name = 'accountId' AND \"notnull\" = 1"),
        )
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM pragma_table_info('categories') WHERE name = 'dimensionId' AND \"notnull\" = 1"),
        )
        assertEquals(0L, scalar("SELECT COUNT(*) FROM credit_cards WHERE accountId IS NULL"))
        // A category is not in the chart of accounts at all.
        assertFalse("accountId" in connection.getColumns("categories"))
        assertTrue("isArchived" in connection.getColumns("categories"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM categories WHERE isArchived <> 0"))
    }

    // --- Chart of accounts -------------------------------------------------

    @Test
    fun `given v7 categories when migrated then they become dimensions and never accounts`() {
        Migration7To10.migrate(connection)

        assertEquals(
            "CATEGORY",
            text("SELECT `kind` FROM `dimensions` WHERE `id` = ${dimensionOf(1)}"),
        )
        // No per-category account was ever created — not created and deleted, never created.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` IN ('Food', 'Salary')"))
        // Nor the v9 uncategorized buckets.
        assertEquals(
            0L,
            scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` LIKE 'Sem categoria%'"),
        )
    }

    @Test
    fun `given v7 when migrated then the chart holds exactly two nominal accounts`() {
        Migration7To10.migrate(connection)

        assertEquals(2L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `type` IN ('EXPENSE','INCOME')"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Despesas' AND `type` = 'EXPENSE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Receitas' AND `type` = 'INCOME'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Reconciliação' AND `type` = 'EQUITY'"))
    }

    @Test
    fun `given a credit card when migrated then it becomes a LIABILITY account`() {
        Migration7To10.migrate(connection)

        val cardAccount = scalar("SELECT `accountId` FROM `credit_cards` WHERE `id` = 1")
        assertEquals("LIABILITY", text("SELECT `type` FROM `accounts` WHERE `id` = $cardAccount"))
        assertEquals("Card", text("SELECT `name` FROM `accounts` WHERE `id` = $cardAccount"))
    }

    @Test
    fun `given invoices and categories when migrated then their dimension ids do not collide`() {
        Migration7To10.migrate(connection)

        // One row per invoice plus one per category, each of its own kind and each
        // reachable from its facade.
        assertEquals(3L, scalar("SELECT COUNT(*) FROM `dimensions`"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `dimensions` WHERE `kind` = 'INVOICE'"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM `dimensions` WHERE `kind` = 'CATEGORY'"))
        assertEquals(
            3L,
            scalar(
                "SELECT COUNT(DISTINCT `dimensionId`) FROM (" +
                    "SELECT `dimensionId` FROM `invoices` UNION ALL SELECT `dimensionId` FROM `categories`)"
            ),
        )
    }

    // --- The money ---------------------------------------------------------

    @Test
    fun `given legacy operations when migrated then every transaction sums to zero`() {
        Migration7To10.migrate(connection)

        val unbalanced = scalar(
            "SELECT COUNT(*) FROM (" +
                "SELECT `transactionId`, SUM(`amount`) AS s FROM `entries` GROUP BY `transactionId`, `currency` HAVING s <> 0" +
                ")"
        )
        assertEquals(0L, unbalanced)
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given legacy transactions when migrated then account balances are preserved in cents`() {
        Migration7To10.migrate(connection)

        // A = -50 (expense) - 100 (transfer out) + 30 (adjustment) + 900 (salary) = 780.00.
        assertEquals(78000L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 1"))
        // B received the transfer: +100.00.
        assertEquals(10000L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 2"))
        // C paid 40.00 of the invoice.
        assertEquals(-4000L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 3"))
    }

    @Test
    fun `given the migrated ledger then net worth equals v7 assets minus liabilities`() {
        Migration7To10.migrate(connection)

        // A(+78000) + B(+10000) + C(-4000) + card(-6000) = 78000. The two reconstructed
        // closed accounts are zeroed by their write-off, so a deleted account's money no
        // longer sits in net worth.
        val netWorth = scalar(
            "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e " +
                "JOIN `accounts` a ON a.`id` = e.`accountId` WHERE a.`type` IN ('ASSET', 'LIABILITY')"
        )
        assertEquals(78000L, netWorth)
    }

    @Test
    fun `given a card purchase and payment when migrated then the invoice dimension carries what is owed`() {
        Migration7To10.migrate(connection)

        val invoiceDimension = scalar("SELECT `dimensionId` FROM `invoices` WHERE `id` = 1")
        // Owed = Σ entries tagged with the invoice: only the card legs (purchase -10000,
        // payment +4000). The payment's account leg must NOT be tagged, or it cancels out.
        assertEquals(
            -6000L,
            scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `dimensionId` = $invoiceDimension"),
        )
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `entries` WHERE `accountId` = 3 AND `dimensionId` IS NOT NULL"))
    }

    @Test
    fun `given category legs when migrated then the total lands on the right nominal and dimension`() {
        Migration7To10.migrate(connection)

        // Food is the contra of three expenses: op1 (50.00), op6 (20.00, deleted
        // account) and op7 (15.00, deleted card). Debit-positive: +8500.
        assertEquals(
            8500L,
            scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `dimensionId` = ${dimensionOf(1)}"),
        )
        // Salary is credit-natured: -90000.
        assertEquals(
            -90000L,
            scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `dimensionId` = ${dimensionOf(2)}"),
        )

        // And each landed on the nominal of its own nature — the mistake no balance
        // figure would expose, since a nominal is not monetary.
        assertEquals(
            8500L,
            scalar(
                "SELECT COALESCE(SUM(`amount`), 0) FROM `entries` " +
                    "WHERE `dimensionId` = ${dimensionOf(1)} AND `accountId` = ${accountId("Despesas")}"
            ),
        )
        assertEquals(
            -90000L,
            scalar(
                "SELECT COALESCE(SUM(`amount`), 0) FROM `entries` " +
                    "WHERE `dimensionId` = ${dimensionOf(2)} AND `accountId` = ${accountId("Receitas")}"
            ),
        )
    }

    @Test
    fun `given an adjustment when migrated then its contra is the reconciliation equity account`() {
        Migration7To10.migrate(connection)

        // op3 has two entries: +3000 on A and -3000 on reconciliation. Scoped to op3,
        // because reconciliation is also the counter-leg of the write-offs.
        assertEquals(
            -3000L,
            scalar(
                "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e " +
                    "JOIN `accounts` a ON a.`id` = e.`accountId` " +
                    "WHERE e.`transactionId` = 3 AND a.`type` = 'EQUITY' AND a.`name` = 'Reconciliação'"
            ),
        )
        // An adjustment classifies nothing: it carries no dimension.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `entries` WHERE `transactionId` = 3 AND `dimensionId` IS NOT NULL"))
    }

    @Test
    fun `given orphaned legs from a deleted account or card when migrated then they become closed accounts`() {
        Migration7To10.migrate(connection) // must not throw on NULL accountId/creditCardId

        // No entry has a null account — a single null would have aborted the whole upgrade.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `entries` WHERE `accountId` IS NULL"))

        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Conta encerrada' AND `type` = 'ASSET' AND `isArchived` = 1"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Cartão encerrado' AND `type` = 'LIABILITY' AND `isArchived` = 1"))

        // Each is zeroed by its write-off, so the money of a deleted account no longer
        // sits in net worth.
        assertEquals(0L, scalar(closedBalance("Conta encerrada")))
        assertEquals(0L, scalar(closedBalance("Cartão encerrado")))

        // The write-off is dated at the account's last movement, not at migration time.
        assertEquals("2024-03-01", text(writeOffDate("Conta encerrada")))
        assertEquals("2024-03-02", text(writeOffDate("Cartão encerrado")))

        assertEquals(0L, wholeLedgerSum())
    }

    private fun closedBalance(name: String) =
        "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e JOIN `accounts` a ON a.`id` = e.`accountId` WHERE a.`name` = '$name'"

    private fun writeOffDate(name: String) =
        "SELECT t.`date` FROM `transactions` t JOIN `entries` e ON e.`transactionId` = t.`id` " +
            "JOIN `accounts` a ON a.`id` = e.`accountId` WHERE a.`name` = '$name' AND t.`title` = 'Encerramento'"

    // --- The v7 states the fixture above does not produce. Each is inserted on top
    // --- of it, so the assertions below also prove the base data is unaffected.

    @Test
    fun `given a leg with no operation when migrated then its money is preserved`() {
        // `transactions.operationId` has been nullable since v1 and no migration ever
        // backfilled it. Discarding such a leg would erase 99.00 from the balance with
        // no error and no trace.
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (NULL,'EXPENSE',99.0,'2024-04-01',1,'ACCOUNT',1)"
        )

        Migration7To10.migrate(connection)

        // Account A: 78000 (base fixture) − 9900 = 68100.
        assertEquals(68100L, scalar("SELECT COALESCE(SUM(amount),0) FROM entries WHERE accountId = 1"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE transactionId IS NULL"))
        // It kept its category, so it counts towards Food: 8500 + 9900.
        assertEquals(
            18400L,
            scalar("SELECT COALESCE(SUM(amount),0) FROM entries WHERE dimensionId = ${dimensionOf(1)}"),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given a multi-leg operation that does not balance when migrated then the residual is reconciled`() {
        // v7 never enforced Σ = 0 on a multi-leg operation, and the migration copies the
        // legs verbatim. An unequal pair would land as permanent corruption that no
        // reader can detect and the write boundary never sees.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (20,'TRANSFER','2024-04-02')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) " +
                "VALUES (20,'EXPENSE',10.0,'2024-04-02','ACCOUNT',1)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) " +
                "VALUES (20,'INCOME',7.0,'2024-04-02','ACCOUNT',2)"
        )

        Migration7To10.migrate(connection)

        assertEquals(0L, scalar("SELECT COALESCE(SUM(amount),0) FROM entries WHERE transactionId = 20"))
        // The 3.00 that did not balance is an explicit equity movement, not a silent hole.
        assertEquals(
            300L,
            scalar(
                "SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id = e.accountId " +
                    "WHERE e.transactionId = 20 AND a.name = 'Reconciliação'"
            ),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an uncategorized expense when migrated then it lands on the nominal with no dimension`() {
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (21,'TRANSACTION','2024-04-03')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (21,'EXPENSE',12.0,'2024-04-03',NULL,'ACCOUNT',1)"
        )

        Migration7To10.migrate(connection)

        assertEquals(
            1200L,
            scalar(
                "SELECT COALESCE(SUM(amount),0) FROM entries " +
                    "WHERE transactionId = 21 AND accountId = ${accountId("Despesas")} AND dimensionId IS NULL"
            ),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an uncategorized income when migrated then it lands on the income nominal`() {
        // The sibling of the case above, and the one a single-nominal mistake would
        // hide: with no category there is nothing but the leg's own type to tell the
        // two nominals apart.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (22,'TRANSACTION','2024-04-04')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (22,'INCOME',33.0,'2024-04-04',NULL,'ACCOUNT',2)"
        )

        Migration7To10.migrate(connection)

        assertEquals(
            -3300L,
            scalar(
                "SELECT COALESCE(SUM(amount),0) FROM entries " +
                    "WHERE transactionId = 22 AND accountId = ${accountId("Receitas")} AND dimensionId IS NULL"
            ),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an expense filed under an income category when migrated then the category nature wins`() {
        // v7 let a leg's type disagree with its category's. The chart row it landed on
        // was the category's own account, so the category's nature decided — and it
        // still must, or the same history would read differently after the upgrade.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (23,'TRANSACTION','2024-04-05')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (23,'EXPENSE',25.0,'2024-04-05',2,'ACCOUNT',1)"
        )

        Migration7To10.migrate(connection)

        assertEquals(
            2500L,
            scalar(
                "SELECT COALESCE(SUM(amount),0) FROM entries " +
                    "WHERE transactionId = 23 AND accountId = ${accountId("Receitas")} " +
                    "AND dimensionId = ${dimensionOf(2)}"
            ),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an operation with no legs when migrated then it produces no entries`() {
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (24,'TRANSACTION','2024-04-06')")

        Migration7To10.migrate(connection)

        assertEquals(1L, scalar("SELECT COUNT(*) FROM transactions WHERE id = 24"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE transactionId = 24"))
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given the write-off ids when migrated then the next insert does not collide`() {
        Migration7To10.migrate(connection)

        val maxBefore = scalar("SELECT COALESCE(MAX(id),0) FROM transactions")
        connection.execSQL("INSERT INTO `transactions` (`title`,`date`) VALUES ('after','2024-05-01')")

        assertEquals(maxBefore + 1, scalar("SELECT COALESCE(MAX(id),0) FROM transactions"))
    }

    @Test
    fun `given recurring occurrences when migrated then they point at the transaction`() {
        connection.execSQL(
            "INSERT INTO `recurring` (`id`,`type`,`amount`,`dayOfMonth`,`createdAt`,`isActive`) VALUES (1,'EXPENSE',50.0,10,1000,1)"
        )
        connection.execSQL(
            "INSERT INTO `recurring_occurrences` (`id`,`recurringId`,`cycleNumber`,`yearMonth`,`status`,`operationId`,`effectiveDate`,`handledAt`) " +
                "VALUES (1,1,1,'2024-01','CONFIRMED',1,'2024-01-10',1000)"
        )

        Migration7To10.migrate(connection)

        assertEquals(1L, scalar("SELECT transactionId FROM recurring_occurrences WHERE id = 1"))
        assertFalse("operationId" in connection.getColumns("recurring_occurrences"))
        assertEquals(
            "transactions",
            text("SELECT \"table\" FROM pragma_foreign_key_list('recurring_occurrences') WHERE \"from\" = 'transactionId'"),
        )
    }

    @Test
    fun `given budgets when migrated then rows survive without the write-only categoryId`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`id`,`categoryId`,`iconCategoryId`,`iconKey`,`title`,`amount`,`period`,`limitType`,`createdAt`) " +
                "VALUES (1,1,1,'food','Comida',500.0,'MONTHLY','FIXED',1000)"
        )
        connection.execSQL("INSERT INTO `budget_categories` (`budgetId`,`categoryId`) VALUES (1,1)")

        Migration7To10.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_table_info('budgets') WHERE name = 'categoryId'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM budgets WHERE id = 1"))
        assertEquals(1L, scalar("SELECT iconCategoryId FROM budgets WHERE id = 1"))
        // The M2M link is the truth, and it survives the category rebuild.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM budget_categories WHERE budgetId = 1 AND categoryId = 1"))
    }

    @Test
    fun `given the migrated database then its integrity and foreign keys hold`() {
        Migration7To10.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE accountId NOT IN (SELECT id FROM accounts)"))
        assertEquals(
            0L,
            scalar("SELECT COUNT(*) FROM entries WHERE dimensionId IS NOT NULL AND dimensionId NOT IN (SELECT id FROM dimensions)"),
        )
        // Enforcement is back on for whatever the app does next.
        assertEquals(1L, scalar("PRAGMA foreign_keys"))
    }

    @Test
    fun `given a leg orphan of both its operation and a deleted account when migrated then no dangling account remains`() {
        // The sharp intersection the ordering of steps 7 and 8 must survive: a leg with
        // BOTH a NULL operationId (never linked to an aggregate) AND a NULL accountId
        // (its account was deleted), and no other deleted-account leg to trigger the
        // closed-account bucket. Step 8 backfills the operationId and step 9 routes the
        // entry to 'Conta encerrada'; if step 7 guarded its EXISTS on
        // `operationId IS NOT NULL` the bucket would not be created and the entry would
        // point at an account that does not exist.
        connection.execSQL("DELETE FROM `transactions` WHERE `operationId` IN (6, 7)")
        connection.execSQL("DELETE FROM `operations` WHERE `id` IN (6, 7)")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (NULL,'EXPENSE',20.0,'2024-03-01',1,'ACCOUNT',NULL)"
        )

        Migration7To10.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE accountId NOT IN (SELECT id FROM accounts)"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM accounts WHERE name = 'Conta encerrada'"))
        assertEquals(0L, scalar(closedBalance("Conta encerrada")))
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an empty v7 database when migrated then the chart still holds its system accounts`() {
        // A fresh install that never recorded anything still has to arrive at a v10 the
        // write boundary can use: without the nominals it would create a second pair.
        val empty = BundledSQLiteDriver().open(":memory:")
        try {
            V7_SCHEMA.forEach(empty::execSQL)

            Migration7To10.migrate(empty)

            val stmt = empty.prepare("SELECT COUNT(*) FROM `accounts`")
            stmt.step()
            assertEquals(3L, stmt.getLong(0))
            stmt.close()
            empty.verifyLedgerBalanced(stage = "empty v7")
        } finally {
            empty.close()
        }
    }

    @Test
    fun `given fractional amounts when migrated then cents are rounded not truncated`() {
        // v7 stored REAL. `0.1 + 0.2` style representations mean a plain CAST would
        // silently lose a cent on values the user typed exactly.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (25,'TRANSACTION','2024-04-07')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (25,'EXPENSE',0.29,'2024-04-07',1,'ACCOUNT',1)"
        )

        Migration7To10.migrate(connection)

        assertEquals(-29L, scalar("SELECT COALESCE(SUM(amount),0) FROM entries WHERE transactionId = 25 AND accountId = 1"))
        assertEquals(0L, wholeLedgerSum())
    }
}
