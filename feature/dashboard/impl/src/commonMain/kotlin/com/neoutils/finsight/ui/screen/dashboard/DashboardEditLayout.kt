package com.neoutils.finsight.ui.screen.dashboard

/**
 * The editor's two lists, and the arithmetic that moves a component between them.
 *
 * The reorderable list hands over a pair of keys — what was dragged, what it was dropped on — and
 * everything else is index arithmetic on a single concatenated list, where the boundary between
 * active and available is just a count. Pulled out of the ViewModel because it is exactly the kind
 * of thing that is expensive to prove through a screen and cheap to prove here: five branches,
 * three sentinel keys, and a handful of off-by-one opportunities.
 *
 * A drop it cannot make sense of leaves the layout untouched rather than throwing — a gesture is
 * allowed to end anywhere, including on a key that no longer exists.
 */
data class DashboardEditLayout(
    val activeItems: List<DashboardEditItem>,
    val availableItems: List<DashboardEditItem>,
) {

    fun move(fromKey: String, toKey: String): DashboardEditLayout {
        val all = activeItems + availableItems
        val fromIndex = all.indexOfFirst { it.key == fromKey }.takeIf { it >= 0 } ?: return this
        val activeCount = activeItems.size

        return when (toKey) {
            // The empty-active slot exists only while nothing is active, so a drop on it can only
            // mean "be the first". Reached any other way it is a stale target.
            EDIT_ACTIVE_PLACEHOLDER_KEY -> {
                if (activeCount != 0) return this
                all.withMoved(fromIndex, 0).splitAt(1)
            }

            // The header and the empty-available slot are the same gesture: cross the boundary.
            // Which way is decided by the side it came from, and it lands right at the edge — the
            // last of the active list or the first of the available one.
            EDIT_SECTION_HEADER_KEY, EDIT_AVAILABLE_PLACEHOLDER_KEY -> {
                val fromInActive = fromIndex < activeCount
                val newActiveCount = if (fromInActive) activeCount - 1 else activeCount + 1
                val landing = if (fromInActive) newActiveCount else activeCount

                all.withMoved(fromIndex, landing).splitAt(newActiveCount)
            }

            // Dropped on another component: it takes that component's place, and the boundary
            // moves only if the two are on opposite sides of it.
            else -> {
                val toIndex = all.indexOfFirst { it.key == toKey }.takeIf { it >= 0 } ?: return this
                val fromInActive = fromIndex < activeCount
                val toInActive = toIndex < activeCount

                val newActiveCount = when {
                    fromInActive && !toInActive -> activeCount - 1
                    !fromInActive && toInActive -> activeCount + 1
                    else -> activeCount
                }

                all.withMoved(fromIndex, toIndex).splitAt(newActiveCount)
            }
        }
    }
}

private fun List<DashboardEditItem>.withMoved(
    fromIndex: Int,
    toIndex: Int,
): List<DashboardEditItem> = toMutableList().apply {
    val moved = removeAt(fromIndex)
    // Clamped against the list it is being inserted into, which is one shorter than the one the
    // indices were read from.
    add(toIndex.coerceAtMost(size), moved)
}

private fun List<DashboardEditItem>.splitAt(activeCount: Int) = DashboardEditLayout(
    activeItems = take(activeCount),
    availableItems = drop(activeCount),
)
