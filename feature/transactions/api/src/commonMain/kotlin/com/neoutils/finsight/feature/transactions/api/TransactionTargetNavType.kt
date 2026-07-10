package com.neoutils.finsight.feature.transactions.api

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finsight.domain.model.Transaction

class TransactionTargetNavType : NavType<Transaction.Target?>(isNullableAllowed = true) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: Transaction.Target?
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
    ): Transaction.Target? {
        return bundle.read { getStringOrNull(key)?.let(Transaction.Target::valueOf) }
    }

    override fun parseValue(value: String): Transaction.Target? {
        return if (value == "null") null else Transaction.Target.valueOf(value)
    }
}
