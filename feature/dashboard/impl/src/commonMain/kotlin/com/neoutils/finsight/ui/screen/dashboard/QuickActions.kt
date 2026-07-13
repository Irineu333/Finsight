package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.feature.shell.api.NavDestination

/**
 * Stable identity for a quick-action destination, used to persist which grid actions the user hid.
 * Derived from the route type so it survives icon/label changes.
 */
internal val NavDestination.actionKey: String
    get() = route::class.simpleName.orEmpty()
