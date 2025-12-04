package com.neoutils.finance.util

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finance.domain.model.Transaction

class TransactionTypeNavType : NavType<Transaction.Type?>(isNullableAllowed = true) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: Transaction.Type?
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
    ): Transaction.Type {
        return bundle.read { Transaction.Type.valueOf(getString(key)) }
    }

    override fun parseValue(value: String): Transaction.Type {
        return Transaction.Type.valueOf(value)
    }
}