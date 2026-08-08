package com.neoutils.finsight.domain.model

/**
 * The keys under which the app finds a category it provides itself.
 *
 * A key, and never a name, because the point of a system category is that the user
 * may adopt it as his own: rename it to "CDI", change its icon, and everything that
 * looks it up keeps working. A lookup by name would break on the first rename, and
 * would collide with a category the user happened to name the same thing.
 *
 * Being keyed here confers nothing else. A system category is a category — listed,
 * offered in selectors, budgetable, and removable once nothing depends on it.
 */
object SystemCategoryKey {

    /**
     * The income category every yield is classified under. It is what separates
     * money that worked on its own from money the user earned, in every read that
     * separates them — through the dimension the category carries.
     */
    const val YIELD = "yield"
}
