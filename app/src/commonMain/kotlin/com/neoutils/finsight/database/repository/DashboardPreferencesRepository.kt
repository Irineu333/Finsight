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

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _preferences = MutableStateFlow(load())
    private val _editTipDismissed = MutableStateFlow(settings.getBoolean(EDIT_TIP_DISMISSED_KEY, false))

    override fun observe(): StateFlow<List<DashboardComponentPreference>?> = _preferences
    override fun observeEditTipDismissed(): StateFlow<Boolean> = _editTipDismissed

    override suspend fun dismissEditTip() {
        settings.putBoolean(EDIT_TIP_DISMISSED_KEY, true)
        _editTipDismissed.value = true
    }

    override suspend fun save(preferences: List<DashboardComponentPreference>) {
        val encoded = json.encodeToString(preferences.map {
            SerializablePreference(key = it.key, position = it.position, config = it.config)
        })
        settings.putString(KEY, encoded)
        _preferences.value = preferences
    }

    private fun load(): List<DashboardComponentPreference>? {
        val encoded = settings.getStringOrNull(KEY) ?: return null
        return runCatching {
            json.decodeFromString<List<SerializablePreference>>(encoded)
                .map {
                    DashboardComponentPreference(
                        key = it.key,
                        position = it.position,
                        config = it.config
                    )
                }
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
        private const val EDIT_TIP_DISMISSED_KEY = "dashboard_edit_tip_dismissed"
    }
}
