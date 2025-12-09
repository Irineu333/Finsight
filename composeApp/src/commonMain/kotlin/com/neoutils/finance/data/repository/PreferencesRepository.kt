package com.neoutils.finance.data.repository

import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun getCreditCardLimit(): Double
    fun setCreditCardLimit(value: Double)
    fun observeCreditCardLimit(): Flow<Double>
}
