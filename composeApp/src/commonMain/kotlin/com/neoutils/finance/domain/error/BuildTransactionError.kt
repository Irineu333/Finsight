package com.neoutils.finance.domain.error

sealed class BuildTransactionError(val message: String) {

    data object AmountRequired : BuildTransactionError(
        message = "O valor é obrigatório."
    )

    data object AmountZero : BuildTransactionError(
        message = "O valor não pode ser zero."
    )

    data object DateRequired : BuildTransactionError(
        message = "A data é obrigatória."
    )

    data object DateFuture : BuildTransactionError(
        message = "A data não pode ser no futuro."
    )

    data object TitleOrCategoryRequired : BuildTransactionError(
        message = "Informe um título ou selecione uma categoria."
    )

    data object CreditCardExpenseOnly : BuildTransactionError(
        message = "Apenas despesas podem ser associadas a cartões de crédito."
    )

    data object InvoiceRequired : BuildTransactionError(
        message = "Fatura é obrigatória para transações de cartão."
    )

    data object CreditCardRequired : BuildTransactionError(
        message = "Cartão de crédito é obrigatório."
    )

    data object AccountRequired : BuildTransactionError(
        message = "Conta é obrigatória para transações de conta."
    )

    data object ClosedInvoice : BuildTransactionError(
        message = "Não é possível adicionar transações em faturas fechadas."
    )
}