@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.unarchiveRecurring

import androidx.compose.runtime.Composable
import com.neoutils.finsight.FakeCrashlytics
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCase
import com.neoutils.finsight.recurring
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unarchiving a recurring is the one confirmed unarchive in the app: it puts a
 * generator back, so it goes through a modal like archiving and deleting do, and
 * carries their behaviour — write the flag, log the event, close the sheet, and on
 * failure say so instead of dying quietly.
 */
class UnarchiveRecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeAnalytics : Analytics {
        val events = mutableListOf<Event>()
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) { events += event }
        override fun setUserId(id: String?) = Unit
    }

    /** Standing in for the sheet, so `dismissAll` is observable from outside. */
    private class FakeModal : Modal() {
        var dismissed = false
        override fun onDismissed() { dismissed = true }

        @Composable
        override fun Content() = Unit
    }

    private fun viewModel(
        repository: FakeRecurringRepository,
        manager: ModalManager,
        analytics: Analytics,
        crashlytics: FakeCrashlytics,
    ) = UnarchiveRecurringViewModel(
        recurring = recurring(isArchived = true),
        unarchiveRecurringUseCase = UnarchiveRecurringUseCase(repository),
        modalManager = manager,
        analytics = analytics,
        crashlytics = crashlytics,
    )

    @Test
    fun `confirming clears the flag and logs the event and closes the sheet`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val manager = ModalManager()
        val analytics = FakeAnalytics()
        val crashlytics = FakeCrashlytics()
        val modal = FakeModal().also(manager::show)

        viewModel(repository, manager, analytics, crashlytics).unarchive()
        advanceUntilIdle()

        assertEquals(listOf(false), repository.updated.map { it.isArchived })
        assertEquals(listOf("unarchive_recurring"), analytics.events.map { it.name })
        assertTrue(modal.dismissed)
        assertTrue(crashlytics.recorded.isEmpty())
    }

    @Test
    fun `a failure is recorded and neither logs the event nor closes the sheet`() = runTest(dispatcher) {
        val failure = IllegalStateException("write failed")
        val repository = FakeRecurringRepository(updateFailure = failure)
        val manager = ModalManager()
        val analytics = FakeAnalytics()
        val crashlytics = FakeCrashlytics()
        val modal = FakeModal().also(manager::show)

        viewModel(repository, manager, analytics, crashlytics).unarchive()
        advanceUntilIdle()

        assertEquals(listOf<Throwable>(failure), crashlytics.recorded)
        assertTrue(analytics.events.isEmpty())
        // The sheet staying open is the point: a closed sheet would read as success.
        assertFalse(modal.dismissed)
    }
}
