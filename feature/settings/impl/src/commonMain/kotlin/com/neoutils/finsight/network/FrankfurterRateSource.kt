package com.neoutils.finsight.network

import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RemoteQuote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * The remote source over **Frankfurter** — free, no key, no SLA — and the only place in
 * this app that speaks HTTP.
 *
 * It is a **writer** of the rate archive and never a path of reading it: nothing waits on
 * it, and what it learns reaches the screens as ordinary rows, offline, by the `Flow` that
 * already existed.
 *
 * **One request per currency in use, and the direction is the point.** The cheap call
 * would be a single `base=<base>&symbols=<every currency>`, which is one request instead of
 * a handful. It is also wrong: it answers *one real is worth 0.18 dollars* and would store
 * rows `(base, currency)` — the archive would stop being *everything priced in the base*
 * and become *the base priced in everything*, inverting the grouping of the rates screen
 * and making every row read backwards from the question the user asked. Correcting that on
 * write would mean inverting the quotient, which the archive forbids outright, because it
 * stores a number nobody observed (design D4).
 *
 * **Nothing thrown escapes this file.** Unavailability is a return value, because failing
 * here has to mean doing nothing.
 */
internal class FrankfurterRateSource(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : IRemoteRateSource {

    /**
     * `/v1/currencies`, which answers the codes the provider quotes, keyed by code.
     *
     * It is asked once per round and it **saves** requests rather than costing them: a
     * currency outside the coverage is settled without a quotation. Its real reason,
     * though, is attribution — a refused quotation names two currencies and cannot say
     * which of them it is about, and the base being the uncovered one is exactly the case
     * where guessing the first end is wrong about every currency at once.
     */
    override suspend fun coverage(): Set<String>? {
        return try {
            val response = client.get("$baseUrl/v1/currencies")

            if (!response.status.isSuccess()) return null

            response.body<Map<String, String>>().keys.takeIf { it.isNotEmpty() }
        } catch (throwable: Throwable) {
            if (throwable is kotlin.coroutines.cancellation.CancellationException) throw throwable
            // Unknown coverage, which is not the same as covering nothing: the caller
            // falls back to asking pair by pair.
            null
        }
    }

    override suspend fun quote(currency: String, against: String): RemoteQuote {
        return try {
            val response = client.get("$baseUrl/v1/latest") {
                parameter("base", currency)
                parameter("symbols", against)
            }

            when {
                // The source refuses an unknown code explicitly, and that is the whole
                // reason *not covered* is a state of its own: waiting will never fix it,
                // and only the user entering the rate by hand will (design D7).
                response.status == HttpStatusCode.NotFound -> RemoteQuote.NotCovered

                !response.status.isSuccess() -> RemoteQuote.Unavailable

                else -> {
                    val body = response.body<FrankfurterLatest>()
                    val rate = body.rates[against]

                    when {
                        rate == null -> RemoteQuote.NotCovered
                        // The date the response declares, never today's (design D5). It
                        // is also what makes synchronising twice over the same
                        // publication inert: the row is the same `(pair, date, REMOTE)`.
                        else -> RemoteQuote.Observed(date = body.date, rate = rate)
                    }
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlin.coroutines.cancellation.CancellationException) throw throwable
            // Transport failure or an unreadable body — it says nothing about the pair.
            RemoteQuote.Unavailable
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.frankfurter.dev"
    }
}
