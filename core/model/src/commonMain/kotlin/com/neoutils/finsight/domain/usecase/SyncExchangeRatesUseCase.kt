package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
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
 * It **composes** and decides nothing that already has an owner: which currencies the app
 * offers is [ICurrencyRepository]'s, which ones are held is [GetAccountCurrenciesUseCase]'s,
 * which one figures read in is the base currency's, what a quotation is is
 * [IRemoteRateSource]'s, and how a row is written is [IExchangeRateRepository.save] — the
 * very same call the harvest and the user's form make. Its own contribution is five rules:
 *
 * **What is covered is what the app *offers*, plus whatever is still held** (design D8b).
 * Covering only what is in use would make the rate **follow** the account instead of
 * preceding it: every first account in a currency would be born in the worst case and stay
 * there until the next day's run. Covering what is offered puts the rate in the archive
 * before an account needs it. An **archived** currency that still has an account or a card
 * stays covered too — archiving is about what is offered, not about what is known, and that
 * account's figure still needs its rate.
 *
 * **Once a day, per currency, on opening.** The cadence is the app's launch, fired and
 * forgotten. The bound is **per currency and never global**: a global one would hold a
 * newly registered currency hostage to a run that already happened that day, though it was
 * never asked about. No background work, no `WorkManager`, no new permission and nothing
 * iOS will not do — because a figure only needs the rate while somebody is looking at it
 * (design D8).
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
    private val currencyRepository: ICurrencyRepository,
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

        val previous = rateSyncStateRepository.observe().value
        val base = baseCurrencyRepository.observe().value

        // What the app offers, plus what is still held — an archived currency with a live
        // account keeps its rate coming. The base is not quoted against itself: it is the
        // axis, not a term.
        val currencies = (currencyRepository.getOffered().map { it.code } + getAccountCurrencies().inUse)
            .distinct()
            .filter { it != base }
            // Per currency, never per round: what already answered today is skipped, and
            // what was never asked has nothing blocking it (design D8b).
            .filter { previous.syncedAt[it]?.toLocalDateTime(timeZone)?.date != today }

        if (currencies.isEmpty()) return

        val syncedAt = previous.syncedAt.toMutableMap()
        val notCovered = previous.notCoveredCurrencies.toMutableSet()

        for (currency in currencies) {
            // The direction asked is the direction the row will be read in — the currency
            // priced in the base — because inverting a quotient to store it would keep a
            // number nobody observed (design D4).
            when (val quote = remoteRateSource.quote(currency = currency, against = base)) {
                is RemoteQuote.Observed -> {
                    exchangeRateRepository.save(
                        ExchangeRate(
                            currency = currency,
                            counterCurrency = base,
                            date = quote.date,
                            rate = quote.rate,
                            source = ExchangeRate.Source.REMOTE,
                        )
                    )
                    syncedAt[currency] = now
                    notCovered -= currency
                }

                // A refusal is an **answer**, and a definitive one, so it stamps the
                // instant like any other: not stamping it would have this currency asked
                // again on every single launch, for ever, to be told the same thing.
                RemoteQuote.NotCovered -> {
                    syncedAt[currency] = now
                    notCovered += currency
                }

                // Unavailability says nothing about the pair. Nothing is written and the
                // instant does not move, so the next launch tries again instead of the
                // daily bound locking out a day that never got its rate.
                RemoteQuote.Unavailable -> Unit
            }
        }

        if (syncedAt == previous.syncedAt && notCovered == previous.notCoveredCurrencies) return

        rateSyncStateRepository.record(
            RateSyncState(syncedAt = syncedAt, notCoveredCurrencies = notCovered),
        )
    }
}
