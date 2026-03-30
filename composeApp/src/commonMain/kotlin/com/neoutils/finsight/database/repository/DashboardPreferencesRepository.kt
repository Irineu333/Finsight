package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DashboardPreferencesRepository(
    private val settings: Settings,
) : IDashboardPreferencesRepository {

    private val _preferences = MutableStateFlow(load())

    override fun observe(): StateFlow<List<DashboardComponentPreference>?> = _preferences

    override suspend fun save(preferences: List<DashboardComponentPreference>) {
        val json = Json.encodeToString(preferences.map {
            SerializablePreference(key = it.key, position = it.position, config = it.config)
        })
        settings.putString(KEY, json)
        _preferences.value = preferences
    }

    private fun load(): List<DashboardComponentPreference>? {
        val json = settings.getStringOrNull(KEY) ?: return null
        return runCatching {
            Json.decodeFromString<List<SerializablePreference>>(json)
                .map { DashboardComponentPreference(key = it.key, position = it.position, config = it.config) }
        }.getOrNull()
    }

    @Serializable
    private data class SerializablePreference(
        val key: String,
        val position: Int,
        val config: Map<String, String> = emptyMap(),
    )

    companion object {
        private const val KEY = "dashboard_preferences"
    }
}
