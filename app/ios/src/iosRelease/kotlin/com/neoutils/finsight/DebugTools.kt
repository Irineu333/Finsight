package com.neoutils.finsight

import org.koin.core.module.Module

/**
 * A release build adds nothing to the app's graph: it runs on the definitions the core modules
 * ship, the clock among them. `MainViewController` aggregates the same list in both
 * configurations, so the shell states the intent once and the source set decides whether there is
 * anything in it.
 */
val debugModules: List<Module> = emptyList()

/**
 * The release counterpart of the time-travel hook: there is no launch argument to read, and the
 * app's clock is [kotlin.time.Clock.System] and nothing else.
 */
fun applyTimeTravel() = Unit
