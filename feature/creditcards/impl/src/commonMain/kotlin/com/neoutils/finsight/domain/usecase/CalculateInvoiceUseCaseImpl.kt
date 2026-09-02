package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.model.Invoice

class CalculateInvoiceUseCaseImpl(
    private val entryRepository: IEntryRepository,
) : CalculateInvoiceUseCase {

    override suspend fun invoke(
        invoices: Collection<Invoice>,
        excluding: Long?,
    ): Map<Long, Double> {
        val dimensionIds = invoices.mapNotNull { it.dimensionId }

        // One read for every dimension asked about, so N invoices cost one query.
        val owedByDimension = if (dimensionIds.isEmpty()) {
            emptyMap()
        } else {
            entryRepository.owedByDimensionByCurrency(dimensionIds)
        }

        // What the excluded operation contributes, read exactly the way the figures
        // above are — entries are `Long` cents, debit-positive, and the owed is `Double`
        // units read credit-positive. Stated as a subtraction of the same reading, the
        // sign falls out on its own and does not depend on whether a payment debits or
        // credits. One read for the whole batch, and none at all when nothing is left out.
        val contributedByDimension = excluding
            ?.let { operation ->
                entryRepository.getEntriesByTransaction(operation)
                    .filter { it.dimensionId != null }
                    .groupBy { it.dimensionId!! }
                    .mapValues { (_, entries) -> -entries.sumOf { it.amount } / 100.0 }
            }
            .orEmpty()

        return invoices.associate { invoice ->
            val dimensionId = invoice.dimensionId
            val owed = dimensionId
                ?.let { owedByDimension[it]?.singleOrNull()?.value }
                ?: 0.0

            invoice.id to owed - (dimensionId?.let { contributedByDimension[it] } ?: 0.0)
        }
    }
}
