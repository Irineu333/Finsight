package com.neoutils.finance.util

sealed class Validation {
    data class Error(val error: UiText) : Validation()
    data object Valid : Validation()
    data object Validating : Validation()
    data object Waiting : Validation()
}
