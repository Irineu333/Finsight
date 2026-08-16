package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency
import kotlinx.datetime.LocalDate

/**
 * Report figures derived entirely from the ledger, via a single SQL aggregate
 * ([IEntryRepository.scopeStatsByCurrency]) rather than by summing a loaded transaction
 * list in memory. This use case only resolves the perspective into the ledger accounts the
 * report is "seen from" — a perspective's ASSET accounts (all of them, including
 * archived, when none are selected, so an archived account's history is not silently
 * dropped) or a card's single LIABILITY account — mirroring
 * `CalculateReportCategorySpendingUseCase`. `income`/`expense` are the period's
 * income/expense magnitudes; `balance` their signed sum (adjustments included);
 * `openingBalance` the signed scope balance before the period. Internal transfers among
 * the scope's accounts are excluded.
 *
 * The figures come back **per currency**. The widest one the app produces — an empty
 * selection means *every* account, archived ones included — is also the one most likely
 * to span currencies, and reducing it is conversion, which belongs above the ledger to
 * whoever knows what date's rates apply.
 */
interface CalculateReportStatsUseCase {
    suspend operator fun invoke(
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency
}
