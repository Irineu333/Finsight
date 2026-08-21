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
 * Deprecating a widget means three things and no more: out of the showcase, out of the
 * default layout, and still rendered wherever it is saved. The third is what makes the
 * first two safe — a key that stopped resolving would cost its owner a position on their
 * own dashboard, silently.
 */
class DashboardDeprecatedWidgetTest {

    private val deprecated = DashboardComponentType.PENDING_BALANCE_STATS
    private val successor = DashboardComponentType.MONTH_SETTLEMENT

    private val previewFactory = DashboardPreviewFactory(
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
        baseCurrencyRepository = FakeBaseCurrencyRepository(),
    )

    // What the edit mode offers, as `DashboardViewModel.buildEditingState` composes it.
    private fun availableKeys(presentKeys: Set<String> = emptySet()) = DashboardComponentType.entries
        .filterNot { it.isDeprecated }
        .filterNot { it.key in presentKeys }
        .map { it.key }

    private fun defaults() = GetDashboardPreferencesUseCase(
        repository = NothingSavedRepository,
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )()

    @Test
    fun `the deprecated widget is not offered for adding`() = runTest {
        assertFalse(deprecated.key in availableKeys())
    }

    @Test
    fun `its successor is`() = runTest {
        assertContains(availableKeys(), successor.key)
        // No preview, no entry in the list — the other half of the filter.
        assertNotNull(previewFactory.createPreview(successor.key, on = LocalDate(2026, 3, 31)))
    }

    /**
     * `buildEditingState` drops every saved key the factory cannot preview, so without
     * this the widget would disappear from the edit mode of whoever still has it — with
     * no way left to remove or reorder it.
     */
    @Test
    fun `the deprecated widget still previews, so its owner can still edit it`() = runTest {
        assertNotNull(previewFactory.createPreview(deprecated.key, on = LocalDate(2026, 3, 31)))
    }

    @Test
    fun `the default layout carries the successor and not the deprecated widget`() = runTest {
        val keys = defaults().first().map { it.key }

        assertContains(keys, successor.key)
        assertFalse(deprecated.key in keys)
    }

    /** The swap is a swap of position: the successor inherits the place and the config. */
    @Test
    fun `the successor takes the position and the hiding rule of what it replaced`() = runTest {
        val entry = defaults().first().single { it.key == successor.key }

        assertEquals(2, entry.position)
        assertEquals("true", entry.config[DashboardComponentConfig.HIDE_WHEN_EMPTY])
    }

    @Test
    fun `a dashboard that saved the deprecated widget reloads exactly as it was`() = runTest {
        val saved = listOf(
            DashboardComponentPreference(
                key = DashboardComponentType.TOTAL_BALANCE.key,
                position = 0,
                config = emptyMap(),
            ),
            DashboardComponentPreference(
                key = deprecated.key,
                position = 1,
                config = mapOf(DashboardComponentConfig.HIDE_WHEN_EMPTY to "true"),
            ),
        )
        val settings = MapSettings()
        DashboardPreferencesRepository(settings).save(saved)

        val reloaded = DashboardPreferencesRepository(settings).observe().value

        assertEquals(saved, reloaded, "deprecating rewrites nothing")
        assertNotNull(reloaded)
        assertFalse(reloaded.any { it.key == successor.key }, "and adds nothing either")
    }
}

/** Nothing saved — the dashboard that reads the default on every load. */
private object NothingSavedRepository : IDashboardPreferencesRepository {
    override fun observe(): StateFlow<List<DashboardComponentPreference>?> = MutableStateFlow(null)
    override fun observeEditTipDismissed(): StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun dismissEditTip() = Unit
    override suspend fun save(preferences: List<DashboardComponentPreference>) = Unit
}
