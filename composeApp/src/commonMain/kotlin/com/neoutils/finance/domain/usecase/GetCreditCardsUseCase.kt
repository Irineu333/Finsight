package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow

class GetCreditCardsUseCase(
    private val repository: ICreditCardRepository
) {
    operator fun invoke(): Flow<List<CreditCard>> = repository.observeAllCreditCards()
}
