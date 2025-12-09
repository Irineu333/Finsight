package com.neoutils.finance.domain.usecase

import com.neoutils.finance.data.repository.PreferencesRepository

class GetCreditCardLimitUseCase(
    private val repository: PreferencesRepository
) {
    operator fun invoke(): Double {
        return repository.getCreditCardLimit()
    }
}
