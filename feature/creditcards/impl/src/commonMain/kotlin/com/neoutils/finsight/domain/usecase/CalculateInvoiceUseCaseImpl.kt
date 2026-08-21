package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.model.Invoice

class CalculateInvoiceUseCaseImpl(
    private val entryRepository: IEntryRepository,
) : CalculateInvoiceUseCase {

    override suspend fun invoke(invoices: Collection<Invoice>): Map<Long, Double> {
        val dimensionIds = invoices.mapNotNull { it.dimensionId }

        // One read for every dimension asked about, so N invoices cost one query.
        val owedByDimension = if (dimensionIds.isEmpty()) {
            emptyMap()
        } else {
            entryRepository.owedByDimensionByCurrency(dimensionIds)
        }

        return invoices.associate { invoice ->
            invoice.id to (
                invoice.dimensionId
                    ?.let { owedByDimension[it]?.singleOrNull()?.value }
                    ?: 0.0
                )
        }
    }
}
