package com.neoutils.finsight.mcp.contract

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/**
 * A value the call is answered with, and whether **the server chose it**.
 *
 * Echoing only the value would not be enough: the consumer could not tell a period it
 * asked for from one it was given, and two calls it believes identical would then differ.
 */
@Serializable
data class Assumed<T>(
    val value: T,
    /** `true` when the call did not carry this value and the server supplied it. */
    val wasAssumed: Boolean,
)

/**
 * A closed interval of **civil** dates, in the user's time zone. Both ends are inclusive,
 * which is the only reading that does not lose the last day of a month.
 */
@Serializable
data class CivilDateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(start <= end) { "A period starts on or before it ends: $start..$end" }
    }
}

/** Which side of the archived line a listing looked at. */
enum class ArchivedScope {

    /**
     * Archived records are left out — **the default**. It is echoed all the same: without
     * it the consumer reports that something does not exist when it is merely archived.
     */
    EXCLUDED,

    /** Archived and active records alike. */
    INCLUDED,

    /** Archived records only. */
    ONLY,
}

/**
 * Everything the server assumed in order to answer, echoed back so the answer is
 * **reproducible**.
 *
 * Two identical calls have to produce the same number, and that is impossible if the
 * consumer does not know what was assumed. The reference date is the clearest case: read
 * today and read tomorrow, the same call answers differently, and nothing in the response
 * would say why.
 */
@Serializable
data class AssumedDefaults(
    /**
     * The IANA identifier of the zone every date in this response is civil in.
     *
     * Dates on this surface are **civil dates in the user's zone**, never instants: a
     * transaction happened on the 31st, and it did not happen at 03:00 UTC on the 1st.
     */
    val timeZone: String,
    /** The date "now" resolved to — what a balance, an invoice or a rate was read at. */
    val referenceDate: Assumed<LocalDate>,
    /** The period the read was cut by, when it was cut by one at all. */
    val period: Assumed<CivilDateRange>? = null,
    /** Which side of the archived line the listing looked at. */
    val archived: Assumed<ArchivedScope>,
) {
    companion object {

        /** The refusal a date that is not a civil date earns — natural language included. */
        const val CODE_NOT_A_CIVIL_DATE: String = "NOT_A_CIVIL_DATE"

        /**
         * Resolves what the call left unsaid, and records that it did.
         *
         * @param today the date the server is answering at, in [timeZone]. Passed in
         * rather than read from a clock here so that the whole contract stays a pure
         * function of its arguments — and so that a test states the date instead of
         * depending on the day it runs.
         */
        fun resolve(
            today: LocalDate,
            timeZone: TimeZone,
            referenceDate: LocalDate? = null,
            period: CivilDateRange? = null,
            archived: ArchivedScope? = null,
        ) = AssumedDefaults(
            timeZone = timeZone.id,
            referenceDate = Assumed(referenceDate ?: today, wasAssumed = referenceDate == null),
            period = period?.let { Assumed(it, wasAssumed = false) },
            archived = Assumed(archived ?: ArchivedScope.EXCLUDED, wasAssumed = archived == null),
        )
    }
}

/** What a date argument resolved to. */
sealed interface CivilDate {

    data class Accepted(val date: LocalDate) : CivilDate

    data class Refused(val error: ToolError) : CivilDate
}

/**
 * Reads a date argument, accepting **only** an ISO-8601 civil date — `YYYY-MM-DD`.
 *
 * The surface MUST NOT interpret a period expressed in natural language. "Last month"
 * resolves against a calendar, a time zone and an opinion about whether the current month
 * counts, and the consumer that wrote it has no way to learn which opinion it got. It is
 * the consumer that owns the user's phrasing; what crosses this boundary are explicit
 * dates.
 *
 * So `today`, `last month` and `2024-13-01` are refused the same way: as invalid input,
 * naming what was expected.
 */
fun parseCivilDate(raw: String): CivilDate {
    val date = raw.takeIf { CIVIL_DATE.matches(it) }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    return when (date) {
        null -> CivilDate.Refused(
            ToolError.invalidInput(
                code = AssumedDefaults.CODE_NOT_A_CIVIL_DATE,
                message = "Expected a civil date as YYYY-MM-DD in the user's time zone; " +
                    "received `$raw`. Natural-language periods are not interpreted by this server.",
            ),
        )

        else -> CivilDate.Accepted(date)
    }
}

private val CIVIL_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
