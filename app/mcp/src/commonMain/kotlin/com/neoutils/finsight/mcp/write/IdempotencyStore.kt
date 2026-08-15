@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.write

import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolOutcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The stable codes an idempotency decision produces. */
object IdempotencyCodes {

    /**
     * The key has already been used with **different** arguments.
     *
     * A conflict and not a replay, deliberately. Agents reuse strings, and turning the
     * second consignment into a no-op would discard legitimate transactions in silence —
     * the gravest outcome this surface can produce. Nothing is written, and the caller is
     * told to use a new key.
     */
    const val KEY_REUSED_WITH_DIFFERENT_ARGUMENTS: String = "IDEMPOTENCY_KEY_CONFLICT"

    val all: Set<String> = setOf(KEY_REUSED_WITH_DIFFERENT_ARGUMENTS)
}

/** What an idempotency key means for the call that carries it. */
sealed interface Idempotency {

    /** The call has not been seen. It proceeds, and its outcome is remembered under the key. */
    data object Proceed : Idempotency

    /**
     * The very same call was made before. **Nothing is written again**, and the answer is
     * the one the first call produced — which is why these records keep the response and
     * the activity journal does not.
     */
    data class Replay(val outcome: ToolOutcome) : Idempotency

    /** The key was used with other arguments. Nothing is written. */
    data class Conflict(val error: ToolError) : Idempotency
}

/**
 * Where an idempotency key and the answer it produced live — **a place of its own**.
 *
 * It is not the activity journal and could not be: the journal records what was asked and
 * how it ended, and this has to be able to hand back the whole response, byte for byte, so
 * that a repeat is answered rather than re-executed.
 *
 * **The record has a declared lifetime.** Held forever it would be a second, perpetual copy
 * of every write the user's agents ever made; [retention] is how long a key is honoured,
 * and a call arriving after it is a call the server has never seen.
 *
 * In memory, and deliberately: a key is meaningful for the retry window of one agent
 * session, the server lives inside the process that owns the database, and a store that
 * outlived the process would have to be pruned by something that is not running.
 */
class IdempotencyStore(
    private val clock: Clock,
    /** How long a key is honoured. A day covers a session's retries without outliving them. */
    val retention: Duration = DEFAULT_RETENTION,
) {

    private class Record(val fingerprint: String, val outcome: ToolOutcome, val at: Instant)

    private val mutex = Mutex()

    private val records = mutableMapOf<String, Record>()

    /**
     * What [key] means for a call carrying [arguments].
     *
     * The key is evaluated **together with a fingerprint of the arguments**, and that pairing
     * is the whole mechanism: the same key with the same arguments is a repeat, and the same
     * key with different ones is a conflict.
     *
     * @param key `null` when the call carries none, which is always [Idempotency.Proceed] —
     * there is nothing to compare it to.
     */
    suspend fun evaluate(key: String?, arguments: JsonObject): Idempotency {
        if (key == null) return Idempotency.Proceed

        val fingerprint = fingerprintOf(arguments)
        return mutex.withLock {
            prune()
            when (val record = records[key]) {
                null -> Idempotency.Proceed
                else -> when (record.fingerprint) {
                    fingerprint -> Idempotency.Replay(record.outcome)
                    else -> Idempotency.Conflict(
                        ToolError.conflict(
                            code = IdempotencyCodes.KEY_REUSED_WITH_DIFFERENT_ARGUMENTS,
                            message = "The idempotency key `$key` was already used with different arguments. " +
                                "Nothing was written. Use a new key: treating this as a repeat would discard " +
                                "these items in silence.",
                        ),
                    )
                }
            }
        }
    }

    /**
     * Remembers what a call produced, so a repeat is answered instead of re-executed.
     *
     * A call with no key remembers nothing: without one there is nothing a later call could
     * present to be recognised by.
     */
    suspend fun remember(key: String?, arguments: JsonObject, outcome: ToolOutcome) {
        if (key == null) return
        mutex.withLock {
            prune()
            records[key] = Record(fingerprintOf(arguments), outcome, clock.now())
        }
    }

    /** How many keys are currently honoured — what a test asserts pruning by. */
    suspend fun size(): Int = mutex.withLock {
        prune()
        records.size
    }

    private fun prune() {
        val deadline = clock.now() - retention
        records.entries.removeAll { it.value.at <= deadline }
    }

    companion object {

        /**
         * The declared lifetime of a key.
         *
         * Long enough to cover a session's retries — an agent repeats on a timeout, a
         * restart or a decision of its own — and short enough that the responses do not
         * become a second history.
         */
        val DEFAULT_RETENTION: Duration = 24.hours
    }
}

/**
 * A stable digest of the arguments a call carried.
 *
 * Stable means **independent of the order the members were written in**: JSON objects have
 * no order, and two agents serialising the same call differently must not be told they
 * conflict. So the object is canonicalised — members sorted by name, recursively — before
 * being read as one string.
 */
internal fun fingerprintOf(arguments: JsonObject): String = canonical(arguments)

private fun canonical(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries
        .sortedBy { it.key }
        .joinToString(separator = ",", prefix = "{", postfix = "}") { "${it.key}:${canonical(it.value)}" }

    // An array's order is part of what it says — thirty statement lines are not the same
    // call in another sequence — so it is kept exactly as it arrived.
    is JsonArray -> element.joinToString(separator = ",", prefix = "[", postfix = "]") { canonical(it) }

    is JsonPrimitive -> element.toString()
}
