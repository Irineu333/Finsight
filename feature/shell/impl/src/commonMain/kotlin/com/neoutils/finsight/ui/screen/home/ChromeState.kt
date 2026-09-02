package com.neoutils.finsight.ui.screen.home

import com.neoutils.finsight.feature.shell.api.ChromeConfig

/**
 * What the chrome is a picture of: what the destination in focus published, and how much room the
 * window gives.
 *
 * **The width belongs in the target**, because a `Transition` animates its target and nothing else.
 * Read beside it, the width partitions the shell into branches instead — and a branch does not
 * animate, it inserts and removes. Crossing the rail's breakpoint then cut the chrome from one
 * frame to the next, and narrowing was worse than a cut: the bottom bar's slot composed for the
 * first time in that frame and seeded itself from a parent still saying a bar was up, so a bar
 * appeared only to play its own exit.
 *
 * The answers below are the whole reason the two live in one value. Each is a fact about the pair,
 * and none of them is a fact about either half alone — which is why none of them can be a branch.
 */
internal data class ChromeState(
    val config: ChromeConfig,
    val isWideWindow: Boolean,
) {

    /** The selector of a compact window. */
    val isBottomBarVisible: Boolean
        get() = !isWideWindow && config.isBottomBarVisible

    /** The selector from `WIDE` upwards — the same offer, standing beside the content. */
    val isRailVisible: Boolean
        get() = isWideWindow && config.isBottomBarVisible

    /** The action button where it stands beside the content: the rail's header. */
    val isRailButtonVisible: Boolean
        get() = isWideWindow && config.actionButton.isBesideContent
}
