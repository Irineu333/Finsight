package com.neoutils.finsight.database.entity

import androidx.room.Entity

/**
 * What an account was denominated in before the app relabelled it, and what it became.
 *
 * The relabelling itself is irreversible by design — a currency is immutable from the moment
 * an account exists, and the migration is allowed to do what the runtime forbids only because
 * it happens *before* that currency was ever an observable fact. This table is what keeps
 * that from being unaccountable: it is not a rollback, it is the record support consults when
 * a user writes in asking why their balances are suddenly in pounds.
 *
 * One row per account actually touched, and none at all when the device's region agrees with
 * the legacy constant — which is the overwhelming majority of installs.
 */
@Entity(tableName = "account_currency_relabel_log", primaryKeys = ["accountId"])
data class AccountCurrencyRelabelLogEntity(
    val accountId: Long,
    val previousCurrency: String,
    val newCurrency: String,
    val migratedAt: Long,
)
