package com.neoutils.finsight

import org.koin.dsl.module

/**
 * A release build adds nothing to the app's graph: it runs on the definitions the core modules
 * ship, the clock among them. `AndroidApp` aggregates the same module in both build types, so the
 * shell states the intent once and the variant decides whether there is anything in it.
 */
val debugModule = module { }
