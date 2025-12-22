package com.neoutils.finance.domain.errors

data class RegisterCreditCardErrors(
    val emptyName: String = "Credit card name cannot be blank",
    val negativeLimit: String = "Credit card limit cannot be negative",
    val invalidClosingDay: String = "Credit card closing day must be between 1 and 28"
)