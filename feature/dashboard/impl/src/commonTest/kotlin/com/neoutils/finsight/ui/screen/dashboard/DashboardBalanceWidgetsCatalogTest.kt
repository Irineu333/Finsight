package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.usecase.GetDashboardPreferencesUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * The neutral widget joins the catalog like any other — offered by the edit mode, absent
 * from what is already saved — and adding it leaves a dashboard assembled earlier
 * untouched, headers included.
 */
class DashboardBalanceWidgetsCatalogTest {

    private val previewFactory = DashboardPreviewFactory(
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    // The edit mode offers every type that is not already present and has a preview
    // (`DashboardViewModel.buildEditingState`).
    private fun availableKeys(presentKeys: Set<String>) = DashboardComponentType.entries
        .filterNot { it.key in presentKeys }
        .map { it.key }

    @Test
    fun `the neutral widget is offered by the edit mode when absent`() = runTest {
        val savedBefore = setOf(
            DashboardComponentType.TOTAL_BALANCE.key,
            DashboardComponentType.CONCRETE_BALANCE_STATS.key,
            DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key,
        )

        assertContains(availableKeys(savedBefore), DashboardComponentType.OVERALL_BALANCE_STATS.key)
        // No preview, no entry in the list — the other half of the filter.
        assertNotNull(previewFactory.createPreview(
            DashboardComponentType.OVERALL_BALANCE_STATS.key,
            on = LocalDate(2026, 3, 31),
        ))
    }

    @Test
    fun `once present, the neutral widget is no longer offered`() = runTest {
        val present = setOf(DashboardComponentType.OVERALL_BALANCE_STATS.key)

        assertFalse(DashboardComponentType.OVERALL_BALANCE_STATS.key in availableKeys(present))
    }

    @Test
    fun `a fresh dashboard opens with the neutral perimeter, not the accounts one`() = runTest {
        val defaults = GetDashboardPreferencesUseCase(
            repository = EmptyPreferencesRepository,
            navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
        )().first()

        val keys = defaults.map { it.key }
        assertContains(keys, DashboardComponentType.OVERALL_BALANCE_STATS.key)
        assertFalse(DashboardComponentType.CONCRETE_BALANCE_STATS.key in keys)

        // Alone among the flow widgets on a fresh screen, it needs no header to be told
        // apart — like the other two, it opens without one.
        val neutral = defaults.single { it.key == DashboardComponentType.OVERALL_BALANCE_STATS.key }
        assertFalse(neutral.config.showHeader(neutral.key))
    }

    @Test
    fun `the neutral widget and its config survive a save and reload`() = runTest {
        val preference = DashboardComponentPreference(
            key = DashboardComponentType.OVERALL_BALANCE_STATS.key,
            position = 0,
            config = DashboardComponentType.OVERALL_BALANCE_STATS.defaultConfig,
        )
        val settings = MapSettings()

        DashboardPreferencesRepository(settings).save(listOf(preference))
        val reloaded = DashboardPreferencesRepository(settings).observe().value

        assertEquals(listOf(preference), reloaded)
    }

    @Test
    fun `a dashboard saved before this change loads unchanged`() = runTest {
        // What such a dashboard holds: the two flow widgets, no `show_header` of their
        // own, and no trace of the neutral perimeter.
        val saved = listOf(
            DashboardComponentPreference(
                key = DashboardComponentType.CONCRETE_BALANCE_STATS.key,
                position = 0,
                config = emptyMap(),
            ),
            DashboardComponentPreference(
                key = DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key,
                position = 1,
                config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
            ),
        )
        val settings = MapSettings()
        DashboardPreferencesRepository(settings).save(saved)

        val reloaded = DashboardPreferencesRepository(settings).observe().value
        assertEquals(saved, reloaded)

        assertNotNull(reloaded)
        assertFalse(reloaded.any { it.key == DashboardComponentType.OVERALL_BALANCE_STATS.key })
        reloaded.forEach { preference ->
            assertFalse(
                preference.config.showHeader(preference.key),
                "${preference.key} must keep the header it never had",
            )
        }
    }
}

/** Nothing saved — what a freshly installed app sees. */
private object EmptyPreferencesRepository : IDashboardPreferencesRepository {
    override fun observe(): StateFlow<List<DashboardComponentPreference>?> = MutableStateFlow(null)
    override fun observeEditTipDismissed(): StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun dismissEditTip() = Unit
    override suspend fun save(preferences: List<DashboardComponentPreference>) = Unit
}
