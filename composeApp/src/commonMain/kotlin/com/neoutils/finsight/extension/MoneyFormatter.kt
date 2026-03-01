package com.neoutils.finsight.extension

fun String.moneyToDouble(): Double {
    val isNegative = startsWith("-")
    val digits = filter { it.isDigit() }
    val cents = digits.toLongOrNull() ?: return 0.0
    return (if (isNegative) -cents else cents).toDouble() / 100
}
