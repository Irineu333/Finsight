package com.neoutils.finance.domain.errors

data class ValidateCreditCardNameErrors(
    val nameRequired: String = "O nome do cartão não pode ser vazio.",
    val nameAlreadyExists: String = "Já existe um cartão com esse nome."
)