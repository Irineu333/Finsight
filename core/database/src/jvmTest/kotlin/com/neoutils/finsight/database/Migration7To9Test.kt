package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration7To9Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        V7_SCHEMA.forEach(connection::execSQL)

        // Sample data: accounts A(1), B(2); category Food(1, EXPENSE)
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

    @Test
    fun `given v7 when migrated then every facade carries a NOT NULL account`() {
        MIGRATION_7_9.migrate(connection)

        // The whole point of eager creation: a category or card can never exist
        // without its chart-of-accounts row, so no reader has to handle the absence.
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM pragma_table_info('categories') WHERE name = 'accountId' AND \"notnull\" = 1"),
        )
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM pragma_table_info('credit_cards') WHERE name = 'accountId' AND \"notnull\" = 1"),
        )
        assertEquals(0L, scalar("SELECT COUNT(*) FROM categories WHERE accountId IS NULL"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM credit_cards WHERE accountId IS NULL"))
    }

    @Test
    fun `given v7 when migrated then entries table and indices are created`() {
        MIGRATION_7_9.migrate(connection)

        assertTrue(connection.tableExists("entries"))
        assertTrue(connection.indexExists("index_entries_transactionId"))
        assertTrue(connection.indexExists("index_entries_accountId"))

        // The index must be attached to `entries` itself, not merely exist by name.
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM pragma_index_list('entries') WHERE name = 'index_entries_transactionId'"),
        )
    }

    @Test
    fun `given v7 when migrated then the legacy leg table is gone and entries point at transactions`() {
        MIGRATION_7_9.migrate(connection)

        // `operations` took the legacy table's name; the leg table no longer exists.
        assertTrue(connection.tableExists("transactions"))
        assertTrue("kind" !in connection.getColumns("transactions"))

        assertEquals(
            "transactions",
            text("SELECT \"table\" FROM pragma_foreign_key_list('entries') WHERE \"from\" = 'transactionId'"),
        )
    }

    @Test
    fun `given v7 when migrated then accounts gains type and currency columns`() {
        MIGRATION_7_9.migrate(connection)

        val columns = connection.getColumns("accounts")
        assertTrue("type" in columns)
        assertTrue("currency" in columns)
    }

    @Test
    fun `given a category when migrated then it is promoted to an EXPENSE account and linked`() {
        MIGRATION_7_9.migrate(connection)

        val accountId = scalar("SELECT `accountId` FROM `categories` WHERE `id` = 1")
        assertTrue(accountId > 0, "category should be linked to a promoted account")
        val type = connection.prepare("SELECT `type` FROM `accounts` WHERE `id` = $accountId")
        type.step()
        assertEquals("EXPENSE", type.getText(0))
        type.close()
    }

    @Test
    fun `given legacy operations when migrated then every operation sums to zero`() {
        MIGRATION_7_9.migrate(connection)

        // Number of operations whose entries do NOT sum to zero must be zero.
        val unbalanced = scalar(
            "SELECT COUNT(*) FROM (" +
                "SELECT `transactionId`, SUM(`amount`) AS s FROM `entries` GROUP BY `transactionId` HAVING s <> 0" +
                ")"
        )
        assertEquals(0L, unbalanced)
    }

    @Test
    fun `given legacy transactions when migrated then account balance is preserved in cents`() {
        MIGRATION_7_9.migrate(connection)

        // Legacy signed balance of A = -50 (expense) - 100 (transfer out) + 30 (adjustment) = -120.00 -> -12000 cents.
        val balanceA = scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 1")
        assertEquals(-12000L, balanceA)

        // B received the transfer: +100.00 -> +10000 cents.
        val balanceB = scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 2")
        assertEquals(10000L, balanceB)
    }

    @Test
    fun `given the whole ledger when migrated then it sums to zero`() {
        MIGRATION_7_9.migrate(connection)

        assertEquals(0L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries`"))
    }

    @Test
    fun `given a card purchase and payment when migrated then invoice owed is abated and the payer is debited`() {
        MIGRATION_7_9.migrate(connection)

        // Owed = Σ entries tagged with the invoice: only the card legs (purchase -10000,
        // payment +4000). The payment's account leg must NOT be tagged, or it cancels out.
        val invoiceNatural = scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `invoiceId` = 1")
        assertEquals(-6000L, invoiceNatural) // owed = 60.00 (100 purchased - 40 paid)

        // The paying account (C = 3) is debited by the payment.
        assertEquals(-4000L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 3"))

        // The payment's account leg carries no invoice tag.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `entries` WHERE `accountId` = 3 AND `invoiceId` IS NOT NULL"))
    }

    @Test
    fun `given orphaned legs from a deleted account or card when migrated then they become closed accounts`() {
        MIGRATION_7_9.migrate(connection) // must not throw on NULL accountId/creditCardId

        // No entry has a null account — a single null would have aborted the whole upgrade.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `entries` WHERE `accountId` IS NULL"))

        // The orphan of a deleted account is reconstructed as a closed ASSET account,
        // the orphan of a deleted card as a closed LIABILITY one.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Conta encerrada' AND `type` = 'ASSET' AND `isArchived` = 1"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM `accounts` WHERE `name` = 'Cartão encerrado' AND `type` = 'LIABILITY' AND `isArchived` = 1"))

        // Each is zeroed by its write-off, so the money of a deleted account no longer
        // sits in net worth: -2000 and -1500 offset by the 'Encerramento' operations.
        assertEquals(0L, scalar(closedBalance("Conta encerrada")))
        assertEquals(0L, scalar(closedBalance("Cartão encerrado")))

        // The write-off is dated at the account's last movement, not at migration time.
        assertEquals("2024-03-01", text(writeOffDate("Conta encerrada")))
        assertEquals("2024-03-02", text(writeOffDate("Cartão encerrado")))

        // The whole ledger still sums to zero (orphan legs balanced by their category contra).
        assertEquals(0L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries`"))
    }

    private fun closedBalance(name: String) =
        "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e JOIN `accounts` a ON a.`id` = e.`accountId` WHERE a.`name` = '$name'"

    private fun writeOffDate(name: String) =
        "SELECT t.`date` FROM `transactions` t JOIN `entries` e ON e.`transactionId` = t.`id` " +
            "JOIN `accounts` a ON a.`id` = e.`accountId` WHERE a.`name` = '$name' AND t.`title` = 'Encerramento'"

    @Test
    fun `given an adjustment when migrated then its contra is the reconciliation equity account`() {
        // Scoped to op3: reconciliation is also the counter-leg of the closed-account
        // write-offs, so a global sum would no longer characterize the adjustment alone.
        MIGRATION_7_9.migrate(connection)

        // op3 has two entries: +3000 on A and -3000 on the reconciliation EQUITY account.
        val reconciliationSum = scalar(
            "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e " +
                "JOIN `accounts` a ON a.`id` = e.`accountId` " +
                "WHERE e.`transactionId` = 3 AND a.`type` = 'EQUITY' AND a.`name` = 'Reconciliação'"
        )
        assertEquals(-3000L, reconciliationSum)
    }

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

        MIGRATION_7_9.migrate(connection)

        // Account A: −5000 −10000 +3000 (base fixture) −9900 = −21900.
        assertEquals(-21900L, scalar("SELECT COALESCE(SUM(amount),0) FROM entries WHERE accountId = 1"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE transactionId IS NULL"))
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given a multi-leg operation that does not balance when migrated then the residual is reconciled`() {
        // v7 never enforced Σ = 0 on a multi-leg operation, and the migration copies the
        // legs verbatim. An unequal pair would land as permanent corruption that no
        // reader can detect and the write boundary never sees.
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (8,'TRANSFER','2024-04-02')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) " +
                "VALUES (8,'EXPENSE',10.0,'2024-04-02','ACCOUNT',1)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) " +
                "VALUES (8,'INCOME',7.0,'2024-04-02','ACCOUNT',2)"
        )

        MIGRATION_7_9.migrate(connection)

        assertEquals(0L, scalar("SELECT COALESCE(SUM(amount),0) FROM entries WHERE transactionId = 8"))
        // The 3.00 that did not balance is an explicit equity movement, not a silent hole.
        assertEquals(
            300L,
            scalar(
                "SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id = e.accountId " +
                    "WHERE e.transactionId = 8 AND a.name = 'Reconciliação'"
            ),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an uncategorized leg when migrated then it lands in the uncategorized bucket`() {
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (9,'TRANSACTION','2024-04-03')")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (9,'EXPENSE',12.0,'2024-04-03',NULL,'ACCOUNT',1)"
        )

        MIGRATION_7_9.migrate(connection)

        assertEquals(
            1200L,
            scalar(
                "SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id = e.accountId " +
                    "WHERE e.transactionId = 9 AND a.name = 'Sem categoria (despesa)'"
            ),
        )
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given an operation with no legs when migrated then it produces no entries`() {
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (10,'TRANSACTION','2024-04-04')")

        MIGRATION_7_9.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE transactionId = 10"))
        assertEquals(0L, wholeLedgerSum())
    }

    @Test
    fun `given the write-off ids when migrated then the next insert does not collide`() {
        MIGRATION_7_9.migrate(connection)

        val maxBefore = scalar("SELECT COALESCE(MAX(id),0) FROM transactions")
        connection.execSQL("INSERT INTO `transactions` (`title`,`date`) VALUES ('after','2024-05-01')")

        assertEquals(maxBefore + 1, scalar("SELECT COALESCE(MAX(id),0) FROM transactions"))
    }

    @Test
    fun `given recurring occurrences when migrated then they point at the transaction`() {
        connection.execSQL(
            "INSERT INTO `recurring_occurrences` (`id`,`recurringId`,`cycleNumber`,`yearMonth`,`status`,`operationId`,`effectiveDate`,`handledAt`) " +
                "VALUES (1,1,1,'2024-01','CONFIRMED',1,'2024-01-10',1000)"
        )

        MIGRATION_7_9.migrate(connection)

        assertEquals(1L, scalar("SELECT transactionId FROM recurring_occurrences WHERE id = 1"))
    }

    @Test
    fun `given budgets when migrated then rows survive without the write-only categoryId`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`id`,`categoryId`,`iconCategoryId`,`iconKey`,`title`,`amount`,`period`,`limitType`,`createdAt`) " +
                "VALUES (1,1,1,'food','Comida',500.0,'MONTHLY','FIXED',1000)"
        )

        MIGRATION_7_9.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_table_info('budgets') WHERE name = 'categoryId'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM budgets WHERE id = 1"))
        assertEquals(1L, scalar("SELECT iconCategoryId FROM budgets WHERE id = 1"))
    }

    @Test
    fun `given the migrated database then its integrity and foreign keys hold`() {
        MIGRATION_7_9.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE accountId NOT IN (SELECT id FROM accounts)"))
    }

    @Test
    fun `given category legs when migrated then the category total is preserved in cents`() {
        MIGRATION_7_9.migrate(connection)

        // Food is the contra of three expenses: op1 (50.00), op6 (20.00, deleted
        // account) and op7 (15.00, deleted card). Debit-positive, its account holds
        // +8500 — the figure the category screen shows, which no prior test asserted.
        val foodTotal = scalar(
            "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e " +
                "JOIN `categories` c ON c.`accountId` = e.`accountId` WHERE c.`id` = 1"
        )
        assertEquals(8500L, foodTotal)
    }

    @Test
    fun `given the migrated ledger then net worth equals v7 assets minus liabilities`() {
        MIGRATION_7_9.migrate(connection)

        // Net worth = Σ over ASSET+LIABILITY: A(-12000) + B(+10000) + C(-4000) +
        // card(-6000) = -12000. The two reconstructed closed accounts are zeroed by
        // their write-off, so a deleted account's money no longer sits in net worth —
        // the spec figure that only the `Σ = 0` invariant covered before.
        val netWorth = scalar(
            "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e " +
                "JOIN `accounts` a ON a.`id` = e.`accountId` WHERE a.`type` IN ('ASSET', 'LIABILITY')"
        )
        assertEquals(-12000L, netWorth)
    }

    @Test
    fun `given a leg orphan of both its operation and a deleted account when migrated then no dangling account remains`() {
        // The sharp intersection the ordering of steps 6 and 6b must survive: a leg
        // with BOTH a NULL operationId (never linked to an aggregate) AND a NULL
        // accountId (its account was deleted), and no other deleted-account leg to
        // trigger the closed-account bucket. Step 6b backfills the operationId and
        // step 7 routes the entry to 'Conta encerrada'; if step 6 guarded its EXISTS
        // on `operationId IS NOT NULL` the bucket would not be created and the entry
        // would point at an account that does not exist.
        connection.execSQL("DELETE FROM `transactions` WHERE `operationId` IN (6, 7)")
        connection.execSQL("DELETE FROM `operations` WHERE `id` IN (6, 7)")
        connection.execSQL(
            "INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) " +
                "VALUES (NULL,'EXPENSE',20.0,'2024-03-01',1,'ACCOUNT',NULL)"
        )

        MIGRATION_7_9.migrate(connection)

        // Without the fix, foreign_key_check reports the dangling entry.
        assertEquals(0L, scalar("SELECT COUNT(*) FROM pragma_foreign_key_check"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM entries WHERE accountId NOT IN (SELECT id FROM accounts)"))
        // The bucket was created and the orphan's money routed into it, then zeroed.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM accounts WHERE name = 'Conta encerrada'"))
        assertEquals(0L, scalar(closedBalance("Conta encerrada")))
        assertEquals(0L, wholeLedgerSum())
    }

    /** Σ of the whole ledger, which must be zero for every currency at all times. */
    private fun wholeLedgerSum(): Long = scalar("SELECT COALESCE(SUM(amount),0) FROM entries")
}
