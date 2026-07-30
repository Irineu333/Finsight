package com.neoutils.finsight.domain.model

/**
 * The currency a figure falls back to when the device's locale names one the app does not
 * offer — **last resort, and never a product default**.
 *
 * The distinction is the whole point of the constant existing here rather than in the
 * ledger. A default would be an opinion about what currency a user holds money in, and the
 * ledger has none: it records the code an account was created with and nothing more. This
 * is the answer to a narrower question — *the locale said something we cannot honour; what
 * now* — and the only honest one, since refusing to open would be worse than reading totals
 * in a currency the user can change nothing about.
 */
const val LAST_RESORT_CURRENCY: String = "BRL"
