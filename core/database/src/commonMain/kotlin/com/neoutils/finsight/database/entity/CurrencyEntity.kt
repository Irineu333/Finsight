package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One currency the app offers — and the **only** statement about which currencies exist.
 *
 * There is no embedded list beside this table and no overlay on top of it: a code either
 * is a row or is not one. What the app ships is the initial content of these rows, which
 * is why a seeded row is editable exactly like one the user typed.
 *
 * **[name] is null by default, and that is the design.** Storing a name at seeding time
 * would freeze it in the language of the first run — switching the app's language would
 * silently stop translating it. A row keeps a name only when the *user* wrote one; when
 * it does not, the platform names the code at every read, in the current language, and
 * the code itself is the worst case.
 *
 * **[symbol] is always stored**, because it is short, stable across languages, and it is
 * what sits over a value. The platform suggests it in the form, and the user may replace
 * it.
 *
 * The ledger does not know this table exists. `accounts.currency` and `entries.currency`
 * are plain ISO strings with no foreign key here, which is precisely what keeps offering
 * a currency a decision of this layer alone.
 */
@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val symbol: String,
    val name: String? = null,
    // Archiving a currency is a rule about what is *offered*, and it has one line of
    // defence rather than two: the ledger cannot refuse a write over it, because it
    // knows neither this table nor the archived flag.
    val isArchived: Boolean = false,
)
