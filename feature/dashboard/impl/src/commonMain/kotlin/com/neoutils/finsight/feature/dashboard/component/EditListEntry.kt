package com.neoutils.finsight.feature.dashboard.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.neoutils.finsight.feature.dashboard.model.DashboardEditItem
import com.neoutils.finsight.feature.dashboard.state.DashboardUiState

const val EDIT_SECTION_HEADER_KEY = "section_header"
const val EDIT_ACTIVE_PLACEHOLDER_KEY = "active_placeholder"
const val EDIT_AVAILABLE_PLACEHOLDER_KEY = "available_placeholder"

@Composable
fun rememberDashboardEditListEntries(
    state: DashboardUiState.Editing,
): List<EditListEntry> = remember(state.activeItems, state.availableItems) {
    buildList {
        if (state.activeItems.isEmpty()) {
            add(EditListEntry.ActivePlaceholder)
        } else {
            state.activeItems.forEach {
                add(
                    EditListEntry.Component(
                        item = it,
                        isActive = true
                    )
                )
            }
        }

        add(EditListEntry.SectionHeader)

        if (state.availableItems.isEmpty()) {
            add(EditListEntry.AvailablePlaceholder)
        } else {
            state.availableItems.forEach {
                add(
                    EditListEntry.Component(
                        item = it,
                        isActive = false
                    )
                )
            }
        }
    }
}

sealed interface EditListEntry {
    val key: String

    data class Component(
        val item: DashboardEditItem,
        val isActive: Boolean,
    ) : EditListEntry {
        override val key = item.key
    }

    data object ActivePlaceholder : EditListEntry {
        override val key = EDIT_ACTIVE_PLACEHOLDER_KEY
    }

    data object SectionHeader : EditListEntry {
        override val key = EDIT_SECTION_HEADER_KEY
    }

    data object AvailablePlaceholder : EditListEntry {
        override val key = EDIT_AVAILABLE_PLACEHOLDER_KEY
    }
}
