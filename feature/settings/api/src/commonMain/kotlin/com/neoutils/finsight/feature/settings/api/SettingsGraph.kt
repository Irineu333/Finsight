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
 * There is no separate base-currency screen. v1 does not offer changing the base
 * (design D18/D28), so that screen would be one value and one paragraph — a whole
 * destination to hold a sentence. When changing it is offered, the section becomes
 * interactive where it already is, and no route moves.
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
