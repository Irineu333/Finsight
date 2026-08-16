package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The stretch of time an answer is about, and **whether it has finished**.
 *
 * A month that has ended and a month that is halfway through are not comparable as equals, and the
 * difference is not recoverable from the payload: two totals side by side, one of them measured over
 * eleven days, read as a fall in spending to anything that receives them. It is the failure a real
 * agent walked into during the simulation this surface was designed against, and the correction is
 * not a caveat in prose — it is a field.
 *
 * [measuredThrough] is the last day the figures actually cover: the period's own end once it has
 * passed, and today while it has not. A period still running is therefore self-describing — the
 * consumer can say "R$ 1.200 through the 14th" instead of "R$ 1.200 in March".
 */
@Serializable
internal data class AgentPeriod(
    /** The calendar month, as `2026-03`, when the period is one. `null` for an arbitrary range. */
    val month: String? = null,
    /** The first day the figures cover. `null` for an accumulation with no start — a balance. */
    val from: LocalDate? = null,
    /** The last day the period covers, whether or not it has arrived. */
    val to: LocalDate,
    /** Whether [to] is still in the future on the app's own clock. */
    @SerialName("is_in_progress")
    val isInProgress: Boolean,
    /** The last day the figures actually cover: [to], or today while the period is running. */
    @SerialName("measured_through")
    val measuredThrough: LocalDate,
) {
    companion object {

        /** A calendar month, running while today falls inside it or before it. */
        fun of(month: YearMonth, today: LocalDate): AgentPeriod = range(
            from = month.firstDay,
            to = month.lastDay,
            today = today,
            month = month.toString(),
        )

        /**
         * An accumulation with no beginning — every posting up to [to]. It has no [from] because
         * there is none: a balance is not a period, and giving it one would invite a difference
         * nobody measured.
         */
        fun upTo(to: LocalDate, today: LocalDate, month: String? = null): AgentPeriod = AgentPeriod(
            month = month,
            from = null,
            to = to,
            isInProgress = to > today,
            measuredThrough = minOf(to, today),
        )

        /** An arbitrary range, which a report asks for. */
        fun range(
            from: LocalDate,
            to: LocalDate,
            today: LocalDate,
            month: String? = null,
        ): AgentPeriod = AgentPeriod(
            month = month,
            from = from,
            to = to,
            isInProgress = to > today,
            measuredThrough = minOf(to, today),
        )
    }
}

/**
 * Two periods put side by side, with the differences **already taken**.
 *
 * The subtraction is done here and not left to whoever reads the answer, for the reason the whole
 * family exists: the app calculates, the agent receives the number. Money in two currencies does not
 * subtract into one, so a difference is a figure like any other — decomposed per currency, reduced
 * only by the one reducer, and honest about what no rate reached.
 *
 * [incompleteSide] is what keeps a variation from being read as a trend. It names which of the two
 * periods had not finished when the figures were taken, and is `null` only when both had.
 */
@Serializable
internal data class AgentComparison(
    /** The period the answer's own figures are being compared **against**. */
    val period: AgentPeriod,
    /** `this_period`, `compared_period`, `both`, or `null` when both had ended. */
    @SerialName("incomplete_side")
    val incompleteSide: String? = null,
    val changes: List<AgentChange>,
)

/** One figure's movement between the two periods of an [AgentComparison]. */
@Serializable
internal data class AgentChange(
    /** Which figure moved — `income`, `expense`, and so on, named as the answer names it. */
    val figure: String,
    val current: AgentFigure,
    val compared: AgentFigure,
    /** `current − compared`, per currency, then reduced. Never left for the reader to take. */
    val difference: AgentFigure,
    /**
     * The change as a percentage of the compared period, or `null` when there is no answer: the
     * earlier figure was zero, or no rate could place the two on one scale. Never `0` standing in
     * for "unknown" — a zero here is a claim that nothing moved.
     */
    @SerialName("percent_change")
    val percentChange: Double? = null,
)
