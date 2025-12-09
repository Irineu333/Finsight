package com.neoutils.finance.util

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.neoutils.finance.domain.model.Transaction

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
    ): Transaction.Target {
        return bundle.read { Transaction.Target.valueOf(getString(key)) }
    }

    override fun parseValue(value: String): Transaction.Target {
        return Transaction.Target.valueOf(value)
    }
}
