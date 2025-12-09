package com.neoutils.finance.domain.usecase

import com.neoutils.finance.data.repository.PreferencesRepository

class SetCreditCardLimitUseCase(
    private val repository: PreferencesRepository
) {
    operator fun invoke(value: Double) {
        require(value >= 0) { "Credit card limit must be non-negative" }
        repository.setCreditCardLimit(value)
    }
}
