package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel

/**
 * **How a listing is ordered and cut into pages** — the two things a paginated answer cannot get
 * wrong without losing an item.
 *
 * Ordering and paging are *adaptation*, and belong to the tool. What they must be is stated by the
 * surface's own requirement: the order is **total**, so two identical calls come back identical and
 * walking the pages neither repeats a posting nor drops one.
 */
internal enum class ListingOrder(val wireName: String) {

    /**
     * By the date of the posting, newest first.
     *
     * The tie-break is what makes it total. A date has a **day**'s resolution, so a day with four
     * postings has twenty-four orders that are all "by date" — and `drop(50).take(50)` over an
     * unstable one is how a page repeats an item the previous page already had.
     */
    DATE("date"),

    /**
     * By **when it was recorded**, newest first — a different question from [DATE], and one the
     * date cannot answer.
     *
     * "The last thing I entered" is not about the day the purchase happened, and with a day's
     * resolution the date has nothing to say about it: three postings dated the 7th were still
     * typed in some order. The tool offers the criterion so that nobody has to guess it from an
     * identifier.
     */
    RECORDED("recorded");

    companion object {

        val wireNames: List<String> = entries.map { it.wireName }

        /** The order asked for, or [DATE] — what a listing means when nobody says. */
        fun of(wireName: String?): ListingOrder =
            entries.firstOrNull { it.wireName == wireName } ?: DATE
    }
}

/**
 * The listing in a **total** order.
 *
 * Both orders end in the identity the ledger assigns on insert, which is what makes them total: it
 * is unique by construction, so no two postings ever compare equal and the sort is the same one
 * every time. That the recording order happens to *be* that identity is this function's business
 * and nobody else's — what the surface offers is the criterion, not the column.
 */
internal fun List<Transaction>.inOrder(order: ListingOrder): List<Transaction> = when (order) {
    ListingOrder.DATE -> sortedWith(
        compareByDescending<Transaction> { it.date }.thenByDescending { it.id },
    )

    ListingOrder.RECORDED -> sortedByDescending { it.id }
}

/**
 * One page of a listing, with the size of the whole beside it.
 *
 * [matching] is the count of what the filter reaches, not of what came back. The two together are
 * what let a consumer know there is more — a page of fifty is indistinguishable from a complete
 * answer of fifty without them.
 */
internal class Page<T>(
    val items: List<T>,
    val matching: Int,
    val offset: Int,
) {
    val returned: Int get() = items.size

    val hasMore: Boolean get() = offset + items.size < matching
}

internal fun <T> List<T>.pageOf(offset: Int, limit: Int): Page<T> = Page(
    items = drop(offset).take(limit),
    matching = size,
    offset = offset,
)

/**
 * The natures a listing can be cut by, spelled as the agent spells them.
 *
 * The values are the ledger's own [TransactionLabel] constants, so a filter of `transfer` selects
 * exactly what `deriveTransactionLabel` calls a transfer — there is no second definition of what a
 * posting *is*, and a tool that re-derived one from a sign would put transfers among the expenses.
 */
internal val NATURES: Map<String, TransactionLabel> =
    TransactionLabel.entries.associateBy { it.name.lowercase() }
