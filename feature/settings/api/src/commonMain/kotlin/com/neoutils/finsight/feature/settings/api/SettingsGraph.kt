package com.neoutils.finsight.feature.settings.api

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

/**
 * The preferences hub. Reached from the nav catalog, immediately before Support — the two
 * are "about the app" rather than about a month's money.
 */
@Serializable
data object SettingsRoute : NavRoute

/**
 * The rates screen, and it is externally navigable on purpose.
 *
 * The designed path to a rate is not the catalog: it is the footer of whatever card just
 * showed an approximate figure, which is the one place where the rate demonstrably matters.
 * That footer lives in another feature, so the destination has to be nameable from outside.
 */
@Serializable
data object ExchangeRatesRoute : NavRoute
