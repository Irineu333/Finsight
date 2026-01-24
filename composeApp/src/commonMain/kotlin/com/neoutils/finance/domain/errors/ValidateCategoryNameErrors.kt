package com.neoutils.finance.domain.errors

data class ValidateCategoryNameErrors(
    val nameRequired: String = "O nome da categoria não pode ser vazio.",
    val nameAlreadyExists: String = "Já existe uma categoria com esse nome."
)