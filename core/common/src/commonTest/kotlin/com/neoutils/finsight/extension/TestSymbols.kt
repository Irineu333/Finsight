package com.neoutils.finsight.extension

/**
 * The symbol table a formatter under test is built over.
 *
 * A table and not the platform, because that is the rule: the glyph over a value is what
 * the registry stored, whatever the machine running the test would have said about the
 * code and in whatever language it reads (design D10).
 */
internal val TEST_SYMBOLS = mapOf(
    "BRL" to "R$",
    "USD" to "$",
    "EUR" to "€",
)
