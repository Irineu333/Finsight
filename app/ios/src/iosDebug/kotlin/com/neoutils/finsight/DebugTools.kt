package com.neoutils.finsight

import com.neoutils.finsight.debug.applyTimeTravel
import com.neoutils.finsight.debug.debugModule
import org.koin.core.module.Module
import platform.Foundation.NSUserDefaults

/**
 * What a debug build adds to the app's graph, which is `:app:debug` and nothing of iOS's own.
 *
 * `MainViewController` aggregates this in both configurations; which of the two source sets is
 * compiled is decided by the `finsight.debugTools` Gradle property, which `iosApp/project.yml`
 * passes only from Xcode's Debug configuration. Kotlin/Native has no build types of its own, so
 * this is the iOS counterpart of Android's `debug`/`release` source sets — and it has the same
 * consequence: a release framework does not contain a movable clock at all, rather than containing
 * one that nothing happens to move.
 */
val debugModules: List<Module> = listOf(debugModule)

/**
 * Reads the clock offset the way iOS delivers it, and hands it to the shared hook.
 *
 * Maestro appends launch arguments to `simctl launch` as `-clockOffsetDays 45`, which is the
 * argument domain of [NSUserDefaults] — the highest-priority domain, read without any of it being
 * persisted. An absent argument reads as `0`, which is the present, and that is the same meaning
 * the Android side gives it.
 */
fun applyTimeTravel() = applyTimeTravel(
    offsetDays = NSUserDefaults.standardUserDefaults.integerForKey(CLOCK_OFFSET_DAYS),
    offsetMonths = NSUserDefaults.standardUserDefaults.integerForKey(CLOCK_OFFSET_MONTHS),
)

private const val CLOCK_OFFSET_DAYS = "clockOffsetDays"
private const val CLOCK_OFFSET_MONTHS = "clockOffsetMonths"
