package com.neoutils.finsight.feature.settings.api

import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsGraph : NavGraphRoute

/**
 * The settings screen: the base currency in force, what it is (and is not) used for,
 * and the way to the rate archive.
 *
 * There is **still** no separate base-currency screen, and now that changing it is
 * offered, that is the interesting half of the sentence: the switch is a modal over this
 * screen, so the section became interactive where it already was and no route moved. A
 * destination of its own would hold one value and one picker.
 */
@Serializable
data object SettingsRoute : NavRoute

/**
 * The rate archive — list, register, correct, remove.
 *
 * Externally navigable on purpose: the catalog is the secondary door. The real one is
 * the footer of any card holding an approximate figure (design D21/D25), which is why
 * this route lives in the `api` rather than inside the graph's `impl` — every feature
 * that renders a consolidated figure has to be able to reach it.
 */
@Serializable
data object ExchangeRatesRoute : NavRoute

/**
 * The registry of currencies — list, register, edit, archive and delete.
 *
 * Externally navigable for the same reason the archive is: what the app offers is data
 * now, and a form that cannot find the currency it needs has to be able to reach the
 * place where one is created, from wherever it is standing.
 */
@Serializable
data object CurrenciesRoute : NavRoute
