package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate

/**
 * One rate of one currency against the user's base, on one date.
 *
 * [rate] is how many units of the base currency one unit of [currency] is worth. There is
 * no matrix of pairs: everything converts to the base, and a cross rate — if one is ever
 * needed — is derived from two rates against it.
 */
data class ExchangeRate(
    val currency: String,
    val date: LocalDate,
    val rate: Double,
    val source: Source,
) {
    /**
     * Where the rate came from, and the tie-breaker when both exist on the same date: the
     * one a person typed wins.
     */
    enum class Source {
        /**
         * Derived from the two ends of a cross-currency operation, on its date. It costs
         * nothing to collect and it is why the user never types the same rate twice.
         */
        OPERATION,

        /** Entered by the user. It prevails on its own date. */
        USER,
    }
}
