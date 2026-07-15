package com.neoutils.finsight.feature.shell.api

/**
 * The ordered catalog of navigable destinations. Provided by `feature:shell:impl` (which builds the
 * concrete routes from each `feature:*:api`) and consumed via Koin by the shell chrome and by the
 * dashboard quick-actions grid.
 */
interface NavCatalog {
    val destinations: List<NavDestination>
}
