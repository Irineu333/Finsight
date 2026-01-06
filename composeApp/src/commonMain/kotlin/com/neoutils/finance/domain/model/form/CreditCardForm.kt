package com.neoutils.finance.domain.model.form

data class CreditCardForm(
    val name: String,
    val limit: Double,
    val closingDay: Int?,
    val dueDay: Int?
)