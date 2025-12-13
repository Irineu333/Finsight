@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AddCreditCardUseCase(
    private val repository: ICreditCardRepository
) {
    suspend operator fun invoke(name: String, limit: Double): Long {
        require(name.isNotBlank()) { "Credit card name cannot be blank" }
        require(limit >= 0) { "Credit card limit must be non-negative" }

        val creditCard = CreditCard(
            name = name,
            limit = limit,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        return repository.insert(creditCard)
    }
}
