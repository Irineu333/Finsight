package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.CurrencyError
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.extension.isTwoDecimalCurrency

/**
 * Registers a currency, or edits one. They are the same write: the code is the identity
 * and is never edited — it is denormalised across accounts, entries, budgets and rates,
 * so changing it would be a data migration rather than an edit.
 *
 * **Decimal places are never a parameter.** Every stored currency has two, and the
 * premise is applied here, where a currency comes into existence, rather than by the
 * curation that no longer exists. A code the platform declares to have zero or three is
 * refused with the reason; one it does not recognise at all is accepted, because an
 * invented code — points, miles — is precisely what this form exists to allow, and the
 * platform has nothing to contradict.
 *
 * Registering creates no account, no rate and no budget. It adds a currency to what the
 * forms offer, and nothing else.
 */
class SaveCurrencyUseCase(
    private val repository: ICurrencyRepository,
) {
    suspend operator fun invoke(
        code: String,
        symbol: String,
        name: String?,
        isEditing: Boolean = false,
    ): Either<CurrencyError, Unit> {
        val normalized = code.trim().uppercase()

        if (normalized.isBlank()) return CurrencyError.CODE_REQUIRED.left()
        if (symbol.isBlank()) return CurrencyError.SYMBOL_REQUIRED.left()

        if (!isTwoDecimalCurrency(normalized)) {
            return CurrencyError.UNSUPPORTED_DECIMALS.left()
        }

        // An edit keeps its own code; only a registration collides with itself.
        if (!isEditing && repository.exists(normalized)) {
            return CurrencyError.CODE_EXISTS.left()
        }

        if (isEditing && !repository.exists(normalized)) {
            return CurrencyError.NOT_FOUND.left()
        }

        repository.save(code = normalized, symbol = symbol.trim(), name = name)

        return Unit.right()
    }
}
