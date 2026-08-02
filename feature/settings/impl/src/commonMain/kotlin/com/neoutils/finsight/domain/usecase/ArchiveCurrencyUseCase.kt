package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.CurrencyError
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository

/**
 * Archives a currency, and unarchives it — a rule about **what is offered**, and only
 * that.
 *
 * It removes nothing. An account in an archived currency stays active, goes on taking
 * entries, and goes on being consolidated; its rate observations stay in the archive and
 * go on being read, so an archived currency still serves as a conversion pivot. Archiving
 * answers *"stop offering me this"*, never *"this is no longer valid"* — exactly what an
 * archived category already means for past transactions.
 *
 * **One line of defence, not two, and deliberately.** Every other archivable thing in
 * this app is also refused at the ledger's write boundary; a currency cannot be, because
 * the ledger knows neither the offered set nor this flag — a currency is a denormalised
 * code on each account and each entry, with no foreign key. Adding the veto there would
 * break the module boundary, so it is not added.
 *
 * The **base** currency is refused: archiving it would leave every consolidated figure
 * denominated in a currency the app declares it no longer offers. Switching the base is
 * the way to archive the one that was base.
 */
class ArchiveCurrencyUseCase(
    private val repository: ICurrencyRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
) {
    suspend fun archive(code: String): Either<CurrencyError, Unit> {
        val normalized = code.uppercase()

        if (normalized == baseCurrencyRepository.observe().value.uppercase()) {
            return CurrencyError.BASE_CURRENCY_NOT_ARCHIVABLE.left()
        }

        repository.archive(normalized)

        return Unit.right()
    }

    suspend fun unarchive(code: String): Either<CurrencyError, Unit> {
        repository.unarchive(code.uppercase())

        return Unit.right()
    }
}
