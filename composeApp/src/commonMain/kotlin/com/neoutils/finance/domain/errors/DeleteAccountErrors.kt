package com.neoutils.finance.domain.errors

data class DeleteAccountErrors(
    val cannotDeleteDefault: String = "Não é possível excluir a conta padrão."
)