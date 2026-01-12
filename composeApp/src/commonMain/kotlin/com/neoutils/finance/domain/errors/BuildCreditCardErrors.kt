package com.neoutils.finance.domain.errors

data class BuildCreditCardErrors(
    val nameRequired: String = "O nome do cartão é obrigatório",
    val nameAlreadyExists: String = "Já existe um cartão com esse nome",
    val limitNegative: String = "O limite não pode ser negativo",
    val closingDayRequired: String = "O dia de fechamento é obrigatório",
    val closingDayInvalid: String = "O dia de fechamento deve ser entre 1 e 31",
    val dueDayRequired: String = "O dia de vencimento é obrigatório",
    val dueDayInvalid: String = "O dia de vencimento deve ser entre 1 e 31",
)
