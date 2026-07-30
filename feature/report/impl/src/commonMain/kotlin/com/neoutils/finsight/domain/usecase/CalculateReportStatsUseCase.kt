package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import kotlinx.datetime.LocalDate

/**
 * Report figures derived entirely from the ledger, via a single SQL aggregate
 * ([IEntryRepository.scopeStats]) rather than by summing a loaded transaction list in
 * memory. This use case only resolves the perspective into the ledger accounts the
 * report is "seen from" — a perspective's ASSET accounts (all of them, including
 * archived, when none are selected, so an archived account's history is not silently
 * dropped) or a card's single LIABILITY account — mirroring
 * [CalculateReportCategorySpendingUseCase]. `income`/`expense` are the period's
 * income/expense magnitudes; `balance` their signed sum (adjustments included);
 * `openingBalance` the signed scope balance before the period. Internal transfers among
 * the scope's accounts are excluded, exactly as before.
 */
class CalculateReportStatsUseCase(
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) {
    suspend operator fun invoke(
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency {
        val scopeAccountIds = when (perspective) {
            is ReportPerspective.AccountPerspective ->
                perspective.accountIds.ifEmpty {
                    accountRepository.getAllAccountsIncludingClosed().map { it.id }
                }

            is ReportPerspective.CreditCardPerspective ->
                listOfNotNull(creditCardRepository.getCreditCardById(perspective.creditCardId)?.accountId)
        }
        // The widest figure the app produces — an empty selection means *every*
        // account, archived ones included — so it is also the one most likely to span
        // currencies. It comes back per currency and is reduced upstream, where the
        // report knows what date's rates apply.
        return entryRepository.scopeStatsByCurrency(scopeAccountIds, startDate, endDate)
    }
}
