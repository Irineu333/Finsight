package com.neoutils.finsight.ui.util

import androidx.compose.ui.Modifier

/**
 * Publishes every `Modifier.testTag` below this node to the platform's accessibility tree, which is
 * the only surface an E2E driver such as Maestro can read.
 *
 * It has to be applied per composition root, and there is no way around that: a modal sheet, a
 * dialog and a popup each open their own window, and a root's semantics do not reach into
 * another's. So every root calls it — the app window (`App`), every sheet
 * ([com.neoutils.finsight.ui.component.ModalBottomSheet] and the phone presentation of
 * [com.neoutils.finsight.ui.component.AdaptiveModal]) and every popup menu whose options are
 * reached by tag. Listing them here would only go stale: what holds is the rule, and a tag that
 * does not reach the driver is a root that has not called this.
 *
 * Only Android needs it: iOS already maps a test tag onto the accessibility identifier, and desktop
 * has no accessibility driver to expose it to.
 */
expect fun Modifier.exposeTestTags(): Modifier
