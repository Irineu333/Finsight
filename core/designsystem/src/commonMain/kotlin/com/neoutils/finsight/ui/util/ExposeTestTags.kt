package com.neoutils.finsight.ui.util

import androidx.compose.ui.Modifier

/**
 * Publishes every `Modifier.testTag` below this node to the platform's accessibility tree, which is
 * the only surface an E2E driver such as Maestro can read.
 *
 * It has to be applied per composition root: a modal sheet, a dialog and a popup each open their
 * own window, and a root's semantics do not reach into another's. Hence the two call sites —
 * [com.neoutils.finsight.ui.component.ModalManagerHost] for the app window and
 * [com.neoutils.finsight.ui.component.ModalBottomSheet] for every sheet.
 *
 * Only Android needs it: iOS already maps a test tag onto the accessibility identifier, and desktop
 * has no accessibility driver to expose it to.
 */
expect fun Modifier.exposeTestTags(): Modifier
