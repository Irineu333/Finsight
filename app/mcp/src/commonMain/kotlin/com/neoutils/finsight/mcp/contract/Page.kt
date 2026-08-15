package com.neoutils.finsight.mcp.contract

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The declared limits of this surface — page size and response size —, in one place
 * because both are answers to the same question: how much a single call may return.
 */
object ResponseLimits {

    /** The page size a listing uses when the call does not ask for one. */
    const val DEFAULT_PAGE_SIZE: Int = 50

    /**
     * The largest page a listing will serve.
     *
     * A limit above it is **refused, naming the ceiling** — never silently truncated. A
     * truncated page is indistinguishable from a complete one to the consumer, which then
     * reports as exhaustive a list it never saw the end of.
     */
    const val MAX_PAGE_SIZE: Int = 200

    /**
     * The largest structured content a single response may carry, in bytes.
     *
     * **It applies to aggregates too**, which do not paginate: a total by category over a
     * period of thousands of transactions is one response, and it has to be bounded by
     * something. A range that would exceed it is refused with guidance on how to narrow
     * it, never dumped.
     */
    const val MAX_RESPONSE_BYTES: Int = 256 * 1024

    /** The refusal a limit above [MAX_PAGE_SIZE] earns — it names the ceiling. */
    const val CODE_PAGE_LIMIT_ABOVE_CEILING: String = "PAGE_LIMIT_ABOVE_CEILING"

    /** The refusal a limit below one earns. */
    const val CODE_PAGE_LIMIT_NOT_POSITIVE: String = "PAGE_LIMIT_NOT_POSITIVE"

    /** The refusal a response too large to serve earns — it says how to reformulate. */
    const val CODE_RESPONSE_TOO_LARGE: String = "RESPONSE_TOO_LARGE"
}

/**
 * Where a listing resumes — **opaque, and never a numeric offset**.
 *
 * An offset duplicates and skips items in the face of concurrent writes: a transaction
 * recorded between two pages shifts every record after it, and the consumer sees one twice
 * and never sees another. The value encodes the key of the last record served, and the
 * encoding exists so that nobody is tempted to do arithmetic on it: a consumer that
 * incremented a cursor would be inventing a position the server never offered.
 */
@Serializable
@JvmInline
value class Cursor(val value: String) {

    /** The key this cursor stands for. */
    fun decode(): String = value
        .chunked(2)
        .map { it.toInt(radix = 16).toByte() }
        .toByteArray()
        .decodeToString()

    companion object {
        /**
         * The cursor of a record whose stable key is [key] — an identifier plus whatever
         * else the listing orders by, joined by the caller.
         */
        fun of(key: String): Cursor = Cursor(
            key.encodeToByteArray().joinToString("") { byte ->
                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            },
        )
    }
}

/**
 * One page of a listing.
 *
 * [totalMatching] is not decoration: without it a consumer cannot tell a complete answer
 * from the first fiftieth of one, and it will present the part as the whole.
 */
@Serializable
data class Page<T>(
    val items: List<T>,
    /** How many records satisfy the filter — all of them, not how many are on this page. */
    val totalMatching: Int,
    /** Where the next page resumes, or `null` when this one ends the listing. */
    val nextCursor: Cursor? = null,
) {
    init {
        require(totalMatching >= items.size) {
            "totalMatching ($totalMatching) cannot be smaller than the page it came with (${items.size})"
        }
    }
}

/** What a requested page size resolved to. */
sealed interface PageLimit {

    /** The size to serve — the requested one, or the default when none was asked for. */
    data class Accepted(val limit: Int, val wasAssumed: Boolean) : PageLimit

    /** The request is refused, and the refusal names the ceiling. */
    data class Refused(val error: ToolError) : PageLimit
}

/**
 * Resolves the page size of a call: the default when none was asked for, the requested one
 * when it fits, and a refusal that **names the ceiling** when it does not.
 *
 * Refusing rather than clamping is the point. Clamping answers a question nobody asked and
 * hides that it did.
 */
fun resolvePageLimit(requested: Int?): PageLimit = when {
    requested == null -> PageLimit.Accepted(ResponseLimits.DEFAULT_PAGE_SIZE, wasAssumed = true)

    requested < 1 -> PageLimit.Refused(
        ToolError.invalidInput(
            code = ResponseLimits.CODE_PAGE_LIMIT_NOT_POSITIVE,
            message = "Page limit must be at least 1; received $requested",
        ),
    )

    requested > ResponseLimits.MAX_PAGE_SIZE -> PageLimit.Refused(
        ToolError.invalidInput(
            code = ResponseLimits.CODE_PAGE_LIMIT_ABOVE_CEILING,
            message = "Page limit $requested exceeds the maximum of ${ResponseLimits.MAX_PAGE_SIZE}; " +
                "request at most ${ResponseLimits.MAX_PAGE_SIZE} items per page",
        ),
    )

    else -> PageLimit.Accepted(requested, wasAssumed = false)
}

/**
 * The refusal a response too large to serve earns, or `null` when it fits.
 *
 * The guidance is required rather than optional: a refusal that does not say how to
 * reformulate leaves the consumer to guess, and it will guess by halving until something
 * works — several more calls over the same data.
 *
 * @param howToNarrow English, imperative, naming the parameter to change — "request a
 * shorter period, or group by month instead of by day".
 */
fun refuseIfOversized(bytes: Int, howToNarrow: String): ToolError? {
    require(howToNarrow.isNotBlank()) { "A size refusal says how to reformulate the call" }

    return if (bytes <= ResponseLimits.MAX_RESPONSE_BYTES) {
        null
    } else {
        ToolError.invalidInput(
            code = ResponseLimits.CODE_RESPONSE_TOO_LARGE,
            message = "Response of $bytes bytes exceeds the maximum of " +
                "${ResponseLimits.MAX_RESPONSE_BYTES} bytes; $howToNarrow",
        )
    }
}
