package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection

/**
 * The four figures the app renders from the ledger, each **keyed by the id of the
 * facade the user sees** — not by the ledger row that happens to carry it today.
 *
 * That keying is the whole point. The v9 → v10 migration replaces the mechanism by
 * which three of these are computed: an invoice stops being an `entries.invoiceId`
 * and becomes a dimension; a category stops being an account and becomes a
 * dimension. A parity assertion written in terms of the mechanism would be rewritten
 * along with it and prove nothing. Written in terms of the facade id, the "before"
 * comes from raw SQL over the old schema and the "after" from the production reads
 * over the new one, and the comparison survives the change it exists to police.
 */
internal data class LedgerFigures(
    val balanceByAccountId: Map<Long, Long>,
    val owedByInvoiceId: Map<Long, Long>,
    val totalByCategoryId: Map<Long, Long>,
    val netWorth: Long,
)

/**
 * Computes the figures by raw SQL over a **v9** database, before any migration runs.
 * Deliberately independent of the DAOs: this is the side of the comparison that must
 * not move when the mechanism does.
 */
internal fun SQLiteConnection.readV9Figures(): LedgerFigures = LedgerFigures(
    balanceByAccountId = queryMap(
        """
        SELECT `a`.`id`, COALESCE(SUM(`e`.`amount`), 0)
        FROM `accounts` `a`
        LEFT JOIN `entries` `e` ON `e`.`accountId` = `a`.`id`
        GROUP BY `a`.`id`
        """
    ),
    owedByInvoiceId = queryMap(
        """
        SELECT `i`.`id`, COALESCE(SUM(`e`.`amount`), 0)
        FROM `invoices` `i`
        LEFT JOIN `entries` `e` ON `e`.`invoiceId` = `i`.`id`
        GROUP BY `i`.`id`
        """
    ),
    // In v9 a category *is* an account; in v10 it is a dimension. Both sides key by
    // `categories.id`, which is what the user's screen actually shows.
    totalByCategoryId = queryMap(
        """
        SELECT `c`.`id`, COALESCE(SUM(`e`.`amount`), 0)
        FROM `categories` `c`
        LEFT JOIN `entries` `e` ON `e`.`accountId` = `c`.`accountId`
        GROUP BY `c`.`id`
        """
    ),
    netWorth = querySum(
        """
        SELECT COALESCE(SUM(`e`.`amount`), 0)
        FROM `entries` `e`
        JOIN `accounts` `a` ON `a`.`id` = `e`.`accountId`
        WHERE `a`.`type` IN ('ASSET', 'LIABILITY')
        """
    ),
)

/**
 * The same figures read back through the **production** queries after the migration.
 * Only this side is rewritten when the mechanism changes.
 */
internal suspend fun AppDatabase.readProductionFigures(): LedgerFigures {
    val entryDao = entryDao()
    return LedgerFigures(
        balanceByAccountId = accountDao().getAllLedgerAccounts()
            .associate { it.id to entryDao.balanceOf(it.id) },
        // Keyed by invoice id, read through the dimension — this is the side that
        // moved, and the only one that should have.
        owedByInvoiceId = invoiceDao().getAllInvoices()
            .associate { it.id to it.dimensionId?.let { d -> entryDao.dimensionNaturalBalance(d) }.orZero() },
        // Archived included: parity is about every figure the ledger can produce,
        // not only the ones a given screen currently lists.
        totalByCategoryId = categoryDao().getAllCategoriesIncludingClosed()
            .associate { it.category.id to entryDao.balanceOf(it.category.accountId) },
        netWorth = entryDao.netWorthCents(),
    )
}

private fun Long?.orZero(): Long = this ?: 0L

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

private fun SQLiteConnection.querySum(sql: String): Long {
    val statement = prepare(sql)
    try {
        statement.step()
        return statement.getLong(0)
    } finally {
        statement.close()
    }
}
