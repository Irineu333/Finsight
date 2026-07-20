package com.neoutils.finsight.feature.transactions.api

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finsight.domain.model.TransactionType

class TransactionTypeNavType : NavType<TransactionType?>(isNullableAllowed = true) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: TransactionType?
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
    ): TransactionType? {
        return bundle.read { getStringOrNull(key)?.let(TransactionType::valueOf) }
    }

    override fun parseValue(value: String): TransactionType? {
        return if (value == "null") null else TransactionType.valueOf(value)
    }
}