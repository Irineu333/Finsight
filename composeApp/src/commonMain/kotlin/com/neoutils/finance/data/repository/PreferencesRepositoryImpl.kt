package com.neoutils.finance.data.repository

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesRepositoryImpl(
    private val settings: Settings
) : PreferencesRepository {

    private val _creditCardLimitFlow = MutableStateFlow(getCreditCardLimit())

    override fun getCreditCardLimit(): Double {
        return settings.getDouble(KEY_CREDIT_CARD_LIMIT, DEFAULT_LIMIT)
    }

    override fun setCreditCardLimit(value: Double) {
        settings.putDouble(KEY_CREDIT_CARD_LIMIT, value)
        _creditCardLimitFlow.value = value
    }

    override fun observeCreditCardLimit(): Flow<Double> {
        return _creditCardLimitFlow.asStateFlow()
    }

    companion object {
        private const val KEY_CREDIT_CARD_LIMIT = "credit_card_limit"
        private const val DEFAULT_LIMIT = 0.0
    }
}
