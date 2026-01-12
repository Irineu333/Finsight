package com.neoutils.finance.extension

import kotlin.math.abs

fun String.parseToMoney(): Double {
    val digitsOnly = this
        .replace("R$", "")
        .replace(".", "")
        .replace(",", ".")
        .replace("-", "")
        .trim()

    return digitsOnly.toDoubleOrNull() ?: 0.0
}

fun Double.toMoneyFormat(): String {
    val isNegative = this < 0
    val absoluteValue = abs(this)

    val cents = (absoluteValue * 100).toLong()
    val reais = cents / 100
    val centavos = cents % 100

    val reaisFormatted = reais.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()

    val formatted = "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"

    return if (isNegative) "-$formatted" else formatted
}

fun Double.toMoneyFormatWithSign(): String {
    val isNegative = this < 0
    val absoluteValue = abs(this)

    val cents = (absoluteValue * 100).toLong()
    val reais = cents / 100
    val centavos = cents % 100

    val reaisFormatted = reais.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()

    val formatted = "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"

    return when {
        isNegative -> "-$formatted"
        this > 0 -> "+$formatted"
        else -> formatted
    }
}
