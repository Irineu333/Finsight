package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.util.UiText

class ValidateCreditCardNameUseCase(
    private val repository: ICreditCardRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): UiText? {
        if (name.isBlank()) {
            return UiText.Raw("O nome do cartão não pode ser vazio.")
        }

        if (hasDuplicateName(name, ignoreId)) {
            return UiText.Raw("Já existe um cartão com esse nome.")
        }

        return null
    }

    private suspend fun hasDuplicateName(
        name: String,
        ignoreId: Long?
    ): Boolean {
        val creditCards = repository.getAllCreditCards()
        return creditCards.any {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != ignoreId
        }
    }
}
