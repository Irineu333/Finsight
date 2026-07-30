package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import com.neoutils.finsight.database.dao.CurrencyTotal
import com.neoutils.finsight.database.entity.AccountEntity

/**
 * The figures the app renders from the ledger, each **keyed by the id of the facade
 * the user sees** — not by the ledger row that happens to carry it today.
 *
 * That keying is the whole point. The v7 → v10 migration replaces every mechanism
 * these are computed by: a leg stops being a signed `REAL` on a single-sided
 * `transactions` row and becomes a balanced pair of entries in cents; a card stops
 * being a table of its own and becomes a `LIABILITY` account; a category and an
 * invoice become dimensions. A parity assertion written in terms of the mechanism
 * would be rewritten along with it and prove nothing. Written in terms of the facade
 * id, the "before" comes from raw SQL over the legacy schema and the "after" from
 * the production reads over v10, and the comparison survives the change it exists to
 * police.
 */
internal data class LedgerFigures(
    val balanceByAccountId: Map<Long, Long>,
    val owedByCardId: Map<Long, Long>,
    val owedByInvoiceId: Map<Long, Long>,
    val totalByCategoryId: Map<Long, Long>,
    /**
     * The nature of the chart account each category's entries sit on.
     *
     * Without it the other figures are blind to the one mistake this migration is
     * most able to make: sending the `EXPENSE` legs to the `INCOME` nominal. Every
     * balance would still match (the nominals are not monetary), every category total
     * would still match (they are summed by dimension, not by account) — and the
     * chart would be quietly wrong, taking the derived label and display sign with it.
     */
    val nominalNatureByCategoryId: Map<Long, String>,
    val netWorth: Long,
)

/** The signed cents a legacy leg contributes to its own side, debit-positive. */
private const val LEGACY_CENTS =
    "CASE `t`.`type` WHEN 'EXPENSE' THEN -CAST(ROUND(`t`.`amount` * 100) AS INTEGER) " +
        "ELSE CAST(ROUND(`t`.`amount` * 100) AS INTEGER) END"

/**
 * Computes the figures by raw SQL over the **v7** database, before any migration
 * runs. Deliberately independent of the DAOs and of the migration's own SQL: this is
 * the side of the comparison that must not move when the mechanism does.
 */
internal fun SQLiteConnection.readLegacyFigures(): LedgerFigures = LedgerFigures(
    balanceByAccountId = queryMap(
        """
        SELECT `a`.`id`, COALESCE(SUM($LEGACY_CENTS), 0)
        FROM `accounts` `a`
        LEFT JOIN `transactions` `t` ON `t`.`accountId` = `a`.`id` AND `t`.`target` = 'ACCOUNT'
        GROUP BY `a`.`id`
        """
    ),
    // A card's own balance, which in v7 lives nowhere but the sum of its legs. Note
    // the `target` filter: a payment's *account* leg also carries `creditCardId`, and
    // counting it here would abate the debt twice.
    owedByCardId = queryMap(
        """
        SELECT `cc`.`id`, COALESCE(SUM($LEGACY_CENTS), 0)
        FROM `credit_cards` `cc`
        LEFT JOIN `transactions` `t` ON `t`.`creditCardId` = `cc`.`id` AND `t`.`target` = 'CREDIT_CARD'
        GROUP BY `cc`.`id`
        """
    ),
    owedByInvoiceId = queryMap(
        """
        SELECT `i`.`id`, COALESCE(SUM($LEGACY_CENTS), 0)
        FROM `invoices` `i`
        LEFT JOIN `transactions` `t` ON `t`.`invoiceId` = `i`.`id` AND `t`.`target` = 'CREDIT_CARD'
        GROUP BY `i`.`id`
        """
    ),
    // What a category totals is what was filed under it, with the sign of the contra
    // side: an expense is a debit to the category. An adjustment classifies nothing.
    totalByCategoryId = queryMap(
        """
        SELECT `c`.`id`, COALESCE(SUM(-($LEGACY_CENTS)), 0)
        FROM `categories` `c`
        LEFT JOIN `transactions` `t` ON `t`.`categoryId` = `c`.`id` AND `t`.`type` <> 'ADJUSTMENT'
        GROUP BY `c`.`id`
        """
    ),
    // Before, a category declares its own nature and nothing else carries it.
    nominalNatureByCategoryId = queryTextMap(
        """
        SELECT `c`.`id`, `c`.`type`
        FROM `categories` `c`
        WHERE EXISTS (SELECT 1 FROM `transactions` `t` WHERE `t`.`categoryId` = `c`.`id`)
        """
    ),
    // Net worth counts what the user still holds. A leg whose account or card was
    // deleted is not part of it — in v7 that money simply vanished, and v10 records
    // the same fact explicitly, by writing the reconstructed account off.
    netWorth = querySum(
        """
        SELECT COALESCE(SUM($LEGACY_CENTS), 0)
        FROM `transactions` `t`
        WHERE (`t`.`target` = 'ACCOUNT' AND `t`.`accountId` IS NOT NULL)
           OR (`t`.`target` = 'CREDIT_CARD' AND `t`.`creditCardId` IS NOT NULL)
        """
    ),
)

/**
 * The same figures read back through the **production** queries after the migration.
 * Only this side is rewritten when the mechanism changes.
 */
internal suspend fun AppDatabase.readProductionFigures(): LedgerFigures {
    val entryDao = entryDao()
    val accounts = accountDao().getAllLedgerAccounts()
    return LedgerFigures(
        // The user's own accounts: the `ASSET` rows that are still open. The two
        // reconstructed closed accounts are not facades the user ever sees — what
        // happened to their money is answered by `netWorth`.
        balanceByAccountId = accounts
            .filter { it.type == AccountEntity.Type.ASSET && !it.isArchived }
            .associate { it.id to entryDao.balanceOf(it.id).cents() },
        owedByCardId = creditCardDao().getAllCreditCardsList()
            .associate { it.creditCard.id to entryDao.balanceOf(it.creditCard.accountId).cents() },
        // Keyed by invoice id, read through the dimension.
        owedByInvoiceId = invoiceDao().getAllInvoices()
            .associate { it.id to it.dimensionId?.let { d -> entryDao.dimensionNaturalBalance(d).cents() }.orZero() },
        // Archived included: parity is about every figure the ledger can produce,
        // not only the ones a given screen currently lists.
        totalByCategoryId = categoryDao().getAllCategoriesIncludingClosed()
            .associate { it.id to entryDao.dimensionNaturalBalance(it.dimensionId).cents() },
        // After, it is the nature of the nominal the dimension's entries landed on.
        nominalNatureByCategoryId = categoryDao().getAllCategoriesIncludingClosed()
            .mapNotNull { category ->
                accounts
                    .firstOrNull { account ->
                        entryDao.getAll().any { it.dimensionId == category.dimensionId && it.accountId == account.id }
                    }
                    ?.let { category.id to it.type.name }
            }
            .toMap(),
        netWorth = entryDao.netWorthCents().cents(),
    )
}

private fun Long?.orZero(): Long = this ?: 0L

/**
 * The cents of a grouped read, for parity against a legacy database. Every row of every
 * database that can be migrated is denominated in one currency, so a figure that comes back
 * per currency has exactly one group here — and if it ever had two, summing them would be
 * the one thing the ledger refuses, which is why this asserts instead of folding.
 */
internal fun List<CurrencyTotal>.cents(): Long = when (size) {
    0 -> 0L
    1 -> single().total
    else -> error("A legacy database is single-currency, got ${map { it.currency }}")
}

/** The same, for a read scoped to one account: no row means no such account. */
internal fun CurrencyTotal?.cents(): Long = this?.total ?: 0L

private fun SQLiteConnection.queryMap(sql: String): Map<Long, Long> {
    val statement = prepare(sql)
    val result = mutableMapOf<Long, Long>()
    try {
        while (statement.step()) result[statement.getLong(0)] = statement.getLong(1)
    } finally {
        statement.close()
    }
    return result
}

private fun SQLiteConnection.queryTextMap(sql: String): Map<Long, String> {
    val statement = prepare(sql)
    val result = mutableMapOf<Long, String>()
    try {
        while (statement.step()) result[statement.getLong(0)] = statement.getText(1)
    } finally {
        statement.close()
    }
    return result
}

private fun SQLiteConnection.querySum(sql: String): Long {
    val statement = prepare(sql)
    try {
        statement.step()
        return statement.getLong(0)
    } finally {
        statement.close()
    }
}
