package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.repository.RemoteQuote
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Keeping the archive up to date is the app's job, not a chore the user remembers.
 *
 * It **composes** and decides nothing that already has an owner: which currencies exist is
 * [GetAccountCurrenciesUseCase]'s, which one figures read in is the base currency's, what a
 * quotation is is [IRemoteRateSource]'s, and how a row is written is
 * [IExchangeRateRepository.save] — the very same call the harvest and the user's form make.
 * Its own contribution is four rules:
 *
 * **Once a day, on opening.** The cadence is the app's launch, fired and forgotten,
 * bounded by the instant of the last successful run. No background work, no `WorkManager`,
 * no new permission and nothing iOS will not do — because a figure only needs the rate
 * while somebody is looking at it (design D8).
 *
 * **Failing means writing nothing.** An unavailable quotation writes no row, does not
 * stamp the instant and **does not throw**: whoever fires this does not await it, and an
 * exception rising through a `LaunchedEffect` would be the one way the network could reach
 * a screen.
 *
 * **Idempotence comes free from the date being the source's** (design D5). Because the row
 * carries the publication's date, running twice in a day — or on Sunday after Saturday —
 * rewrites the same `(pair, date, REMOTE)` through the `REPLACE` the unique key already
 * guarantees. Nothing duplicates, and nothing has to know it already ran.
 *
 * **A currency the source does not cover is not a failure** and does not stop the others.
 * It is recorded as such, because that is what makes the distinction actionable: *wait* and
 * *enter it by hand* are different actions, and only telling them apart helps (design D7).
 */
class SyncExchangeRatesUseCase(
    private val getAccountCurrencies: GetAccountCurrenciesUseCase,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val remoteRateSource: IRemoteRateSource,
    private val exchangeRateRepository: IExchangeRateRepository,
    private val rateSyncStateRepository: IRateSyncStateRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    suspend operator fun invoke() {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date

        val lastSyncedOn = rateSyncStateRepository.observe().value.lastSyncedAt
            ?.toLocalDateTime(timeZone)?.date
        if (lastSyncedOn == today) return

        val base = baseCurrencyRepository.observe().value

        // The base is not quoted against itself: it is the axis, not a term.
        val currencies = getAccountCurrencies().inUse.filter { it != base }
        if (currencies.isEmpty()) return

        val notCovered = mutableSetOf<String>()
        var unavailable = false

        for (currency in currencies) {
            // The direction asked is the direction the row will be read in — the currency
            // in use priced in the base — because inverting a quotient to store it would
            // keep a number nobody observed (design D4).
            when (val quote = remoteRateSource.quote(currency = currency, against = base)) {
                is RemoteQuote.Observed -> exchangeRateRepository.save(
                    ExchangeRate(
                        currency = currency,
                        counterCurrency = base,
                        date = quote.date,
                        rate = quote.rate,
                        source = ExchangeRate.Source.REMOTE,
                    )
                )

                RemoteQuote.NotCovered -> notCovered += currency
                RemoteQuote.Unavailable -> unavailable = true
            }
        }

        // Nothing reached: the instant stays where it was, so the next launch tries again
        // instead of the daily bound locking out a day that never got its rates.
        if (unavailable) return

        rateSyncStateRepository.record(RateSyncState(lastSyncedAt = now, notCoveredCurrencies = notCovered))
    }
}
