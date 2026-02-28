package com.neoutils.finsight.extension

fun String.moneyToDouble(): Double {
    val digitsOnly = this
        .replace("R$", "")
        .replace(".", "")
        .replace(",", ".")
        .replace("-", "")
        .trim()

    return digitsOnly.toDoubleOrNull() ?: 0.0
}
