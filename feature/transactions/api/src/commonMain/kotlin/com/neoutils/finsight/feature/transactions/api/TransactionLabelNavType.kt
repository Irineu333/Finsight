package com.neoutils.finsight.feature.transactions.api

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finsight.domain.model.TransactionLabel

class TransactionLabelNavType : NavType<TransactionLabel?>(isNullableAllowed = true) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: TransactionLabel?
    ) {
        bundle.write {
            if (value != null) {
                putString(key, value.name)
            }
        }
    }

    override fun get(
        bundle: SavedState,
        key: String
    ): TransactionLabel? {
        return bundle.read { getStringOrNull(key)?.let(TransactionLabel::valueOf) }
    }

    override fun parseValue(value: String): TransactionLabel? {
        return if (value == "null") null else TransactionLabel.valueOf(value)
    }
}
