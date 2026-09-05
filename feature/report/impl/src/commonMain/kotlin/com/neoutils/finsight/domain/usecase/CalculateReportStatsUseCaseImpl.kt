package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import kotlinx.datetime.LocalDate

class CalculateReportStatsUseCaseImpl(
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) : CalculateReportStatsUseCase {

    override suspend fun invoke(
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
