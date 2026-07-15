package com.neoutils.finsight.domain.model

/**
 * The single base currency used by the v1 ledger.
 *
 * Every [Entry] and [Account] carries a currency code so that multi-currency
 * support can be added later without a data-model rewrite; for now only this
 * base currency is used. The zero-sum invariant is enforced per currency.
 */
const val BASE_CURRENCY: String = "BRL"
