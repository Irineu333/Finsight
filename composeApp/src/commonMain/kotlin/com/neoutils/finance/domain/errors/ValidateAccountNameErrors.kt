package com.neoutils.finance.domain.errors

data class ValidateAccountNameErrors(
    val nameRequired: String = "O nome da conta não pode ser vazio.",
    val nameAlreadyExists: String = "Já existe uma conta com esse nome."
)