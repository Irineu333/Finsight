package com.neoutils.finsight.feature.shell.api

import com.neoutils.finsight.isDesktop

/**
 * The platform a feature is restricted to, when it is restricted to one at all.
 *
 * **The axis is symmetric.** It names what does not work on the desktop and what only works there,
 * with one type and one answer, because both are the same question asked from opposite ends: on
 * which platform does this feature exist. Two independent flags would have allowed the fourth
 * combination — a feature that is both mobile-only and desktop-only — which means nothing.
 *
 * **It is not the window's width.** A narrow window on the desktop is still the desktop, and a
 * feature restricted to it stays offered there; nothing about the size of a window can enter
 * [isCurrent], which reads the platform and nothing else. The width governs which affordance the
 * shell draws, and never whether a feature exists.
 */
enum class FeaturePlatform {

    /** Its implementation has no desktop backing — a device capability the JVM target has not. */
    MOBILE,

    /** Its implementation needs what only the desktop process has, such as a socket of its own. */
    DESKTOP,
    ;

    /** Whether this is the platform the app is running on right now. */
    val isCurrent: Boolean get() = (this == DESKTOP) == isDesktop
}
