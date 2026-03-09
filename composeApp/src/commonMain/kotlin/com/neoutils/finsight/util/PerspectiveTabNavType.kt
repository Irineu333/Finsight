package com.neoutils.finsight.util

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab

class PerspectiveTabNavType : NavType<PerspectiveTab>(isNullableAllowed = false) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: PerspectiveTab
    ) {
        bundle.write {
            putString(key, value.name)
        }
    }

    override fun get(
        bundle: SavedState,
        key: String
    ): PerspectiveTab {
        return bundle.read { PerspectiveTab.valueOf(getString(key)) }
    }

    override fun parseValue(value: String): PerspectiveTab {
        return PerspectiveTab.valueOf(value)
    }
}
