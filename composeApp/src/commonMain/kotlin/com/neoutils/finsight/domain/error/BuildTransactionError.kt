package com.neoutils.finsight.domain.error

sealed class BuildTransactionError(val message: String) {

    data object AmountRequired : BuildTransactionError(
        message = "Amount is required."
    )

    data object AmountZero : BuildTransactionError(
        message = "Amount cannot be zero."
    )

    data object DateRequired : BuildTransactionError(
        message = "Date is required."
    )

    data object DateFuture : BuildTransactionError(
        message = "Date cannot be in the future."
    )

    data object TitleOrCategoryRequired : BuildTransactionError(
        message = "Title or category is required."
    )

    data object CreditCardExpenseOnly : BuildTransactionError(
        message = "Only expenses can be associated with credit cards."
    )

    data object InvoiceRequired : BuildTransactionError(
        message = "Invoice is required for credit card transactions."
    )

    data object CreditCardRequired : BuildTransactionError(
        message = "Credit card is required."
    )

    data object AccountRequired : BuildTransactionError(
        message = "Account is required for account transactions."
    )

    data object ClosedInvoice : BuildTransactionError(
        message = "Cannot add transactions to closed invoices."
    )
}