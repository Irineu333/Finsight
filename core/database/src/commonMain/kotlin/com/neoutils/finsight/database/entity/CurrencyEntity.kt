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
 * **[symbol] is always stored, and [name] is not — for the same reason, read the other
 * way round.** A glyph is not stable across languages either: the dollar is `$` to
 * someone reading in English and `US$` to someone reading in Portuguese. But unlike a
 * name, the glyph is *what sits over a value*, so it must be one thing the user can fix,
 * and fixing it must survive the next read. It is therefore resolved once — from the
 * reader's locale, when the row is born — and never re-resolved, because re-resolving it
 * would overwrite an edit the user made deliberately.
 *
 * The cost is stated rather than hidden: someone who seeds in one language and then
 * switches the device to another keeps the glyph the first language chose, while [name]
 * follows the switch. Editing the row is the way out, and it is the same gesture that
 * makes a seeded row no different from a typed one.
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
