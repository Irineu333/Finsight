package com.neoutils.finance.util

data class FieldForm(
    val text: String = "",
    val validation: Validation = Validation.Waiting,
)

sealed class Validation {
    data class Error(val error: UiText) : Validation()
    data object Valid : Validation()
    data object Validating : Validation()
    data object Waiting : Validation()
}