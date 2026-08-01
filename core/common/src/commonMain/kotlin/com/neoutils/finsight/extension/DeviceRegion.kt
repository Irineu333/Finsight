package com.neoutils.finsight.extension

/**
 * The currency of the region the device is actually **in** — or `null` when nothing on
 * this platform corroborates one.
 *
 * This is the *strong* half of what [localeCurrencyCode] answers, and it exists because
 * the two questions were being asked with one read and they are not the same question.
 * A locale carries a country because a language is written differently in different
 * places; it does not follow that the user is there. On iOS the distinction is a setting
 * — Region is chosen apart from Language — but on Android the country comes from the
 * language list, so a Brazilian who reads the interface in *English (United States)* has
 * a locale that says `US` while every account they own is in reais.
 *
 * That difference is harmless when the answer only picks a pre-selection, which is all
 * [localeCurrencyCode] is used for now. It is not harmless for the one-shot relabelling
 * of design D30, which **re-denominates every row of an existing database** and cannot be
 * undone: there the question really is "where is this money", and only a signal about
 * location may answer it.
 *
 * **Silence is an answer.** A platform that cannot corroborate a region returns `null`,
 * and the relabelling then leaves the data alone. Falling back to the locale would put
 * back exactly the read this type exists to stop being trusted, for exactly the devices
 * where it is least trustworthy.
 *
 * It is an interface rather than a function because on Android the answer needs the
 * application context, and a global that reaches for one is the kind of hidden
 * dependency this module does not have anywhere else.
 */
fun interface DeviceRegion {

    fun currencyCode(): String?
}
