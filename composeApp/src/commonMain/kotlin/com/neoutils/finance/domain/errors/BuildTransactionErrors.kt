package com.neoutils.finance.domain.errors

data class BuildTransactionErrors(
    val amountRequired: String = "O valor é obrigatório.",
    val amountZero: String = "O valor não pode ser zero.",
    val dateRequired: String = "A data é obrigatória.",
    val dateInvalid: String = "Formato de data inválido.",
    val dateFuture: String = "A data não pode ser no futuro.",
    val titleOrCategoryRequired: String = "Informe um título ou selecione uma categoria.",
    val creditCardExpenseOnly: String = "Apenas despesas podem ser associadas a cartões de crédito.",
    val invoiceRequired: String = "Fatura é obrigatória para transações de cartão.",
    val creditCardRequired: String = "Cartão de crédito é obrigatório.",
    val invoiceNotOpen: String = "A fatura precisa estar aberta.",
    val creditCardMismatch: String = "O cartão não corresponde à fatura.",
    val dateOutsideInvoicePeriod: String = "A data deve estar dentro do período da fatura.",
)
