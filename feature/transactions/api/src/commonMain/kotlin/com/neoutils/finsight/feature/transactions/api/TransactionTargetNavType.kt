package com.neoutils.finsight.feature.transactions.api

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finsight.domain.model.TransactionTarget

class TransactionTargetNavType : NavType<TransactionTarget?>(isNullableAllowed = true) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: TransactionTarget?
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
    ): TransactionTarget? {
        return bundle.read { getStringOrNull(key)?.let(TransactionTarget::valueOf) }
    }

    override fun parseValue(value: String): TransactionTarget? {
        return if (value == "null") null else TransactionTarget.valueOf(value)
    }
}
