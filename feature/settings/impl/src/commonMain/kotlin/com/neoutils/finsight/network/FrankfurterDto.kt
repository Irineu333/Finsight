package com.neoutils.finsight.network

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * What Frankfurter answers a quotation with:
 *
 * ```json
 * { "amount": 1.0, "base": "USD", "date": "2026-07-31", "rates": { "BRL": 5.0583 } }
 * ```
 *
 * @param date **the date of the publication**, which is the one the archive row is
 * stamped with. The source publishes on working days, so a Sunday answers with Friday's;
 * writing today's would invent an observation about a day nobody observed anything on.
 * @param rates counter currency to quotient, in the direction that was asked. Only the
 * one symbol requested is ever in here.
 */
@Serializable
internal data class FrankfurterLatest(
    val base: String = "",
    val date: LocalDate,
    val rates: Map<String, Double> = emptyMap(),
)
