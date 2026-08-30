package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.ExchangeRateDao
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The rate archive, plus the one removal that carries the person's answer about the copy.
 *
 * [IExchangeRateRepository] lives in `:core:model` and is implemented by every module that
 * has to answer a rate; the second [remove] is this feature's business alone — an
 * observation is removed from exactly one screen — so it is declared here instead of
 * obliging those fakes to know that backup exists. It is an interface rather than the
 * concrete class only so that the form's test can go on faking the archive.
 */
interface RateArchive : IExchangeRateRepository {

    /**
     * @param withoutCopy the person's answer, after being told that the copy owed before
     * this removal could not be taken: remove anyway, with nothing kept back.
     */
    suspend fun remove(rate: ExchangeRate, withoutCopy: Boolean)
}

/**
 * The rate archive over the `exchange_rates` table, and **the owner of the declared
 * precedence**.
 *
 * Two policies meet here and they are deliberately not the same one:
 *
 * - *the last observation on or before the date, per pair, ties on that date broken by
 *   origin — `USER` ▸ `REMOTE` ▸ `DERIVED`* is the DAO's query, stated once, in SQL;
 * - *direct ▸ inverse ▸ one pivot* is [resolve], pure and over what that query returned.
 *
 * [IExchangeRateRepository] has always promised rates **against the base**. That used to
 * be true by accident — there was only ever one base, and every row was on its axis.
 * Here it becomes true by construction, which is why `ConsolidateMoneyUseCase`, the view
 * models and every screen that shows a figure are untouched by the switch existing
 * (design D4).
 *
 * The dependency on [IBaseCurrencyRepository] is what pays for that, and it is the one
 * legitimate place for it: this is the only point that can know at once what is stored
 * and which preference is in force. It replaces an assumption that used to be implicit,
 * and it denominates no figure.
 *
 * [PreventiveBackup] is here for [remove], and here rather than in the form above it: an
 * observation has no use case of its own, so this public method **is** the action, and it
 * is the boundary every caller of it crosses (design D6). It sits above any transaction —
 * removing one row opens none — which is what `VACUUM INTO` requires.
 */
class ExchangeRateRepository(
    private val dao: ExchangeRateDao,
    private val mapper: ExchangeRateMapper,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val preventiveBackup: PreventiveBackup,
) : RateArchive {

    override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? {
        return rateBetween(currency, baseCurrencyRepository.observe().value, date)
    }

    override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> {
        val base = baseCurrencyRepository.observe().value
        val observations = dao.ratesAsOf(date).map(mapper::toDomain)

        return observations
            .flatMap { listOf(it.currency, it.counterCurrency) }
            .distinct()
            .filter { it != base }
            .mapNotNull { currency ->
                observations.resolve(currency, base)?.let { currency to answer(currency, base, date, it) }
            }
            .toMap()
    }

    override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? {
        // The direct level short-circuits to a single indexed query, which is the common
        // case by a wide margin: it answers with the stored row itself, id and origin
        // included, instead of the derived answer below.
        dao.rateOfPairAsOf(from, to, date)?.let { return mapper.toDomain(it) }

        val resolved = dao.ratesAsOf(date).map(mapper::toDomain).resolve(from, to) ?: return null
        return answer(from, to, date, resolved)
    }

    /**
     * A rate the archive **implies** rather than holds.
     *
     * `id = 0` because it is no row: it was read out of the observations, not stored
     * beside them, and offering it an id would invite something to write it back — that
     * is still what keeps the implied answer from returning to the archive.
     *
     * The origin is **the one the observations it was read from actually have**: the
     * origin of the single observation when there is one — the inverse being that same
     * observation read backwards — and the weakest of the two in a triangulation.
     * Labelling everything `DERIVED` was legitimate while the field meant *"not the
     * user's"*; with three origins it would be a claim about where a number came from
     * that is simply untrue.
     */
    private fun answer(from: String, to: String, date: LocalDate, resolved: ResolvedRate) = ExchangeRate(
        currency = from,
        counterCurrency = to,
        date = date,
        rate = resolved.rate,
        source = resolved.source,
    )

    /**
     * The rate **in force** for every pair, as of [date] — one row per pair, elected by
     * the archive's own policy in the DAO's query.
     *
     * **A member of this type and not of [IExchangeRateRepository]**, deliberately. It is
     * a read only the rates screen makes, and putting it on the interface would oblige
     * the thirteen fakes that implement it to answer a question their modules never ask.
     * The interface's signatures are what `ConsolidateMoneyUseCase`, the view models and
     * every screen showing a figure depend on, and none of them moved.
     */
    fun observeInForce(date: LocalDate): Flow<List<ExchangeRate>> {
        return dao.observeInForce(date).map { rates -> rates.map(mapper::toDomain) }
    }

    override fun observeAll(): Flow<List<ExchangeRate>> {
        return dao.observeAll().map { rates -> rates.map(mapper::toDomain) }
    }

    override suspend fun save(rate: ExchangeRate) {
        // One write, not two. `REPLACE` over the unique
        // `(currency, counterCurrency, date, source)` is what makes registering and
        // correcting the same operation: a correction is a `USER` row that coexists with
        // the `DERIVED` one it outranks instead of destroying the observation the
        // operation itself made.
        dao.insert(mapper.toEntity(rate))
    }

    override suspend fun remove(rate: ExchangeRate) = remove(rate, withoutCopy = false)

    /**
     * The copy comes first, and a copy owed and not taken throws out of here without the
     * row being touched. An observation is typed work — it may be the correction that
     * outranked a wrong rate — and nothing derives it back once it is gone.
     *
     * [withoutCopy] is a person's answer to that refusal and nothing else: which actions are
     * worth a copy is still stated here, by naming
     * [DestructiveAction.REMOVE_EXCHANGE_RATE], whatever the answer was (design D7).
     */
    override suspend fun remove(rate: ExchangeRate, withoutCopy: Boolean) {
        if (!withoutCopy) preventiveBackup.captureBefore(DestructiveAction.REMOVE_EXCHANGE_RATE)

        dao.deleteById(rate.id)
    }

    override suspend fun countNaming(currency: String): Int =
        dao.countByCurrencyOnEitherEnd(currency)

    /**
     * No capture of its own: this is the rate half of **deleting a currency**, and that
     * action's copy is taken once by `DeleteCurrencyUseCase`, above the transaction the
     * pair is written in. A second one here would be a second file for one user action —
     * and, reached from inside that transaction, a `VACUUM INTO` that cannot run.
     */
    override suspend fun removeAllNaming(currency: String) {
        dao.deleteByCurrencyOnEitherEnd(currency)
    }
}
