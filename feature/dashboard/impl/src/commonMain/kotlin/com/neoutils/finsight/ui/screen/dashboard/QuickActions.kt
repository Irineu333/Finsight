package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.feature.shell.api.NavDestination

/**
 * Stable identity for a quick-action destination, used to persist which grid actions the user hid.
 * Derived from the route type so it survives icon/label changes.
 */
internal val NavDestination.actionKey: String
    get() = route::class.simpleName.orEmpty()

/**
 * Quick-action keys the user hid, parsed from persisted config. Legacy [QuickActionType] enum names
 * (persisted before the enum→catalog migration) are normalized to the current route-based
 * [actionKey] so prior hide/show choices survive an update. Drop [LEGACY_ACTION_KEYS] once no legacy
 * values remain in storage.
 */
internal fun parseHiddenActionKeys(config: Map<String, String>): Set<String> =
    config[QuickActionsConfig.HIDDEN_ACTIONS]
        ?.split(",")
        ?.filter { it.isNotEmpty() }
        ?.mapTo(mutableSetOf()) { LEGACY_ACTION_KEYS[it] ?: it }
        .orEmpty()

private val LEGACY_ACTION_KEYS = mapOf(
    "BUDGETS" to "BudgetsRoute",
    "CATEGORIES" to "CategoriesRoute",
    "CREDIT_CARDS" to "CreditCardsRoute",
    "ACCOUNTS" to "AccountsRoute",
    "RECURRING" to "RecurringRoute",
    "REPORTS" to "ReportGraph",
    "INSTALLMENTS" to "InstallmentsRoute",
    "SUPPORT" to "SupportGraph",
)
