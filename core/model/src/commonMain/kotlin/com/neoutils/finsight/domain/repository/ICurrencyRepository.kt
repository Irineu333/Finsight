package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.CurrencyInfo
import kotlinx.coroutines.flow.Flow

/**
 * The set of currencies the app offers — the **single** source of it.
 *
 * There is no embedded list beside this and no overlay on top of it: a code either is a
 * row or is not one, so there is no union to compute and no precedence rule to keep
 * correct. What the app ships is the initial content of these rows, which is why a seeded
 * row is editable exactly like one the user typed.
 *
 * **The name arrives resolved.** A row keeps a name only when the user wrote one; when it
 * does not, the platform names the code in the current language, and the code itself is
 * the worst case. Resolving it at every read is what keeps a name from freezing in the
 * language of the run that stored it.
 *
 * **The ledger MUST NOT consult this contract.** It persists the currency code and
 * nothing else — `accounts.currency` and `entries.currency` are plain ISO strings with no
 * foreign key here — and which set the app offers is a decision of the consolidation
 * layer alone. That is also why an archived currency has one line of defence and not two:
 * it disappears from what is offered, and the ledger has nothing to refuse.
 */
interface ICurrencyRepository {

    /** The currencies a form may offer — the archived ones excluded. */
    fun observeOffered(): Flow<List<CurrencyInfo>>

    /** Every row, archived included — what the registry screen lists. */
    fun observeAll(): Flow<List<CurrencyInfo>>

    /** The currencies a form may offer, read once. */
    suspend fun getOffered(): List<CurrencyInfo>

    /** Every row, archived included, read once. */
    suspend fun getAll(): List<CurrencyInfo>

    /** The row with this code, or `null` when no row has it. */
    suspend fun get(code: String): CurrencyInfo?

    /** Whether a row already has this code — what refuses a duplicate registration. */
    suspend fun exists(code: String): Boolean

    /**
     * Registers or edits a currency. The code is the identity and is never edited: it is
     * denormalised across accounts, entries, budgets and rates, so changing it would be a
     * data migration rather than an edit.
     *
     * A `null` [name] means "the platform names it" and is the default state of a row —
     * only a name the user wrote is stored.
     */
    suspend fun save(code: String, symbol: String, name: String?)

    /** Stops offering this currency, without invalidating anything that already uses it. */
    suspend fun archive(code: String)

    /** Offers it again, exactly as before. */
    suspend fun unarchive(code: String)

    /**
     * Removes the currency **and every rate observation that names it, in one write**.
     *
     * Whether it may be removed at all is decided above this — that is a rule, and rules
     * live in the use case. That the two removals are one unit of work is not a rule but
     * a property of the write, and it belongs here, where a transaction can be opened.
     *
     * The pair is indivisible in both directions. An observation left behind goes on
     * being a **conversion path**, since the resolver reads the archive without consulting
     * the offered set; and a currency left behind after its rates are gone is a row the
     * user already asked to delete, now quoting nothing. Either half alone is a state the
     * app has no name for.
     */
    suspend fun delete(code: String)
}
