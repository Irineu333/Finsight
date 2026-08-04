package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RatePair
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.repository.RemoteQuote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 * **Once a day, per pair, on opening.** The cadence is the app's launch, fired and
 * forgotten. The bound is **per pair, never global and never per currency**: a global one
 * would hold a newly registered currency hostage to a run that already happened that day,
 * and a per-currency one would do the same to every pair the moment the base changed. No
 * background work, no `WorkManager`, no new permission and nothing iOS will not do —
 * because a figure only needs the rate while somebody is looking at it (design D8).
 *
 * **When it becomes due is [whenDue], and it is this use case's rule** rather than the
 * shell's: what makes the upkeep owe another round is a fact about the upkeep.
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
 * **Which** currency is uncovered is asked of the source rather than inferred from a
 * refusal: a refusal names a pair and cannot say which end it is about, and when the
 * uncovered code is the **base** every pair is refused at once — attributing that to the
 * first end would fill the screen with one false sentence per currency the user holds.
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

    /**
     * Every moment the upkeep owes a round: the app opening, the set of offered
     * currencies **changing**, and the base currency **changing**.
     *
     * The registry trigger is *changing* and not *gaining* on purpose. Gaining is the case
     * that has to fire — a currency nothing has ever been asked about — and narrowing the
     * signal to it would mean keeping the previous set to diff against, to save requests
     * that the per-pair bound already costs nothing. Losing or archiving one fires a round
     * in which every remaining pair is skipped, which is no round at all.
     *
     * The last one is not decoration. The archive is *everything priced in the base*, so
     * switching the base makes a whole set of pairs — the ones against the new base —
     * become the rows that answer, and none of them has ever been fetched. Without this,
     * they would arrive only on the next day's launch, which is the same worst case the
     * per-pair bound exists to remove (design D8d).
     *
     * Collecting this is **safe to the point of being boring** precisely because the bound
     * is per pair: everything already answered today is skipped, so a round fired by a
     * change that turns out to matter to nothing costs no request at all. That is what lets
     * the shell collect it without deciding anything.
     *
     * The rule lives here and not in the shell so that `App` never has to name the base
     * currency — which the reach guard pins by name, and rightly: a screen naming it is
     * almost always a figure about to wear the wrong denomination.
     */
    fun whenDue(): Flow<Unit> = combine(
        currencyRepository.observeOffered().map { rows -> rows.map { it.code }.toSet() },
        baseCurrencyRepository.observe(),
    ) { offered, base -> offered to base }
        // Editing a currency's symbol or name changes the registry and changes nothing
        // here: what is owed depends on *which* currencies exist and on the base, and on
        // nothing else about them.
        .distinctUntilChanged()
        .map { }

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
            // Per **pair**, never per round and never per currency: what already answered
            // today is skipped, and what was never asked has nothing blocking it. The pair
            // is what is fetched, so it is what the bound has to be about — keyed by the
            // currency alone, switching the base would leave every currency looking
            // answered while the row that just became the one that matters was never asked
            // for (design D8b, D8d).
            .filter { previous.syncedAt[RatePair(it, base)]?.toLocalDateTime(timeZone)?.date != today }

        if (currencies.isEmpty()) return

        val syncedAt = previous.syncedAt.toMutableMap()
        val notCovered = previous.notCoveredCurrencies.toMutableSet()

        // **Which end is uncovered is asked, never inferred.** A refused quotation names
        // two currencies and says nothing about which of them it refused, and the base is
        // the case where guessing the first end is wrong about every currency at once: an
        // uncovered base refuses every pair, and blaming the currencies would put one
        // false sentence on the screen per currency the user holds, when the true one is
        // a single sentence about the base (design D7).
        val coverage = remoteRateSource.coverage()

        if (coverage != null && base !in coverage) {
            // Nothing can be quoted against it, so nothing is asked. The pairs are stamped
            // like any other definitive answer — not stamping them would have every
            // currency asked again on every launch to be refused again — and the base is
            // what is recorded as uncovered, because it is what is.
            currencies.forEach { syncedAt[RatePair(it, base)] = now }
            rateSyncStateRepository.record(
                RateSyncState(syncedAt = syncedAt, notCoveredCurrencies = notCovered + base),
            )
            return
        }

        // Only on a known coverage. Clearing it on an unreachable source would drop a true
        // statement about the base on the first network blip.
        if (coverage != null) notCovered -= base

        for (currency in currencies) {
            // Known to be outside the coverage: settled without spending a request, and
            // attributed to the currency because the base has just been vouched for.
            if (coverage != null && currency !in coverage) {
                syncedAt[RatePair(currency, base)] = now
                notCovered += currency
                continue
            }

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
                    syncedAt[RatePair(currency, base)] = now
                    notCovered -= currency
                }

                // A refusal is an **answer**, and a definitive one, so it stamps the
                // instant like any other: not stamping it would have this currency asked
                // again on every single launch, for ever, to be told the same thing.
                RemoteQuote.NotCovered -> {
                    syncedAt[RatePair(currency, base)] = now
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
