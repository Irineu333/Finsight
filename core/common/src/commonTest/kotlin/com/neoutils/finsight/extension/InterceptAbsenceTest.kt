package com.neoutils.finsight.extension

import app.cash.turbine.test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InterceptAbsenceTest {

    @Test
    fun presentValuesPassThroughAndReemit() = runTest {
        var missing = 0
        var disappeared = 0

        flowOf(1, 2, 3)
            .interceptAbsence(
                onMissing = { missing++ },
                onDisappeared = { disappeared++ },
            )
            .test {
                assertEquals(1, awaitItem())
                assertEquals(2, awaitItem())
                assertEquals(3, awaitItem())
                awaitComplete()
            }

        assertEquals(0, missing)
        assertEquals(0, disappeared)
    }

    @Test
    fun firstEmissionNullTriggersOnMissingAndEmitsNull() = runTest {
        var missing = 0
        var disappeared = 0

        flowOf<Int?>(null)
            .interceptAbsence(
                onMissing = { missing++ },
                onDisappeared = { disappeared++ },
            )
            .test {
                assertEquals(null, awaitItem())
                awaitComplete()
            }

        assertEquals(1, missing)
        assertEquals(0, disappeared)
    }

    @Test
    fun nullAfterContentTriggersOnDisappearedAndIsSuppressed() = runTest {
        var missing = 0
        var disappeared = 0

        flowOf(1, null)
            .interceptAbsence(
                onMissing = { missing++ },
                onDisappeared = { disappeared++ },
            )
            .test {
                assertEquals(1, awaitItem())
                // the trailing null is suppressed — the caller keeps the last value
                awaitComplete()
            }

        assertEquals(0, missing)
        assertEquals(1, disappeared)
    }

    @Test
    fun repeatedEqualEmissionsAreCollapsed() = runTest {
        var missing = 0
        var disappeared = 0

        flowOf(1, 1, 2)
            .interceptAbsence(
                onMissing = { missing++ },
                onDisappeared = { disappeared++ },
            )
            .test {
                assertEquals(1, awaitItem())
                assertEquals(2, awaitItem())
                awaitComplete()
            }

        assertEquals(0, missing)
        assertEquals(0, disappeared)
    }

    @Test
    fun repeatedNullsAfterContentDisappearOnlyOnce() = runTest {
        var missing = 0
        var disappeared = 0

        flowOf(1, null, null)
            .interceptAbsence(
                onMissing = { missing++ },
                onDisappeared = { disappeared++ },
            )
            .test {
                assertEquals(1, awaitItem())
                awaitComplete()
            }

        assertEquals(0, missing)
        assertEquals(1, disappeared)
    }

    @Test
    fun recoversAfterFirstMissing() = runTest {
        var missing = 0
        var disappeared = 0

        flowOf(null, 2)
            .interceptAbsence(
                onMissing = { missing++ },
                onDisappeared = { disappeared++ },
            )
            .test {
                assertEquals(null, awaitItem())
                assertEquals(2, awaitItem())
                awaitComplete()
            }

        assertEquals(1, missing)
        assertEquals(0, disappeared)
    }
}
