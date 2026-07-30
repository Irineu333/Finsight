package com.neoutils.finsight.extension

import kotlin.math.abs

/**
 * The grammar of a money **field**, read: digits are cents and a leading `-` is the sign.
 *
 * It is not [DisplayAmount]'s job. That type answers how a figure *reads* once it is done
 * being edited, and is free to let the platform place a negative wherever the locale puts
 * it. This text is round-tripped — written by [moneyInput], read back by this — so the two
 * ends have to agree on where the sign goes, and they agree here.
 */
fun String.moneyToDouble(): Double {
    val isNegative = startsWith("-")
    val digits = filter { it.isDigit() }
    val cents = digits.toLongOrNull() ?: return 0.0
    return (if (isNegative) -cents else cents).toDouble() / 100
}

/**
 * The same grammar, written: [cents] in [currency], with the sign concatenated outside the
 * formatted magnitude so that [moneyToDouble] reads back exactly what was written.
 *
 * This is the single owner of the text a money field holds — the seed a modal puts in an
 * empty field and what `MoneyInputTransformation` writes on every keystroke both come from
 * here. Seeding a field by any other rule is how the first keystroke comes to rewrite the
 * amount the user was shown.
 */
fun CurrencyFormatter.moneyInput(cents: Long, currency: String): String {
    val formatted = format(abs(cents).toDouble() / 100, currency)
    return if (cents < 0) "-$formatted" else formatted
}
