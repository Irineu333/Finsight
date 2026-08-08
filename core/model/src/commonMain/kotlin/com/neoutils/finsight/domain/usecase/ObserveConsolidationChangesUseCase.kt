package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Emits whenever anything a consolidated figure depends on has moved.
 *
 * **The promise of reactivity needed a mechanism, because none of the existing ones
 * reached this far.** Nineteen of the ledger's reads are `suspend`, and the app's only
 * reactive trigger is a `SELECT COUNT(*) FROM entries`. Registering, correcting or
 * removing a rate writes no entry, and neither does switching the base currency — which
 * is now a path the user actually walks, in settings, rather than a hypothesis. Without
 * this, a figure would keep whatever value it had when it was last computed and the
 * claim that it reacts would simply be false.
 *
 * It is not new architecture: it is the same seam the view models already use, with two
 * more sources fused into it — the ledger's trigger, the base preference, and the
 * `Flow` Room already gives over the rate table.
 */
class ObserveConsolidationChangesUseCase(
    private val entryRepository: IEntryRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
) {
    operator fun invoke(): Flow<Unit> = combine(
        entryRepository.observeLedgerChanges(),
        baseCurrencyRepository.observe(),
        exchangeRateRepository.observeAll(),
    ) { _, _, _ -> Unit }
}
