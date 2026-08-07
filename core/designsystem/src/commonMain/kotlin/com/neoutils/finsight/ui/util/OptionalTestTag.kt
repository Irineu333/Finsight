package com.neoutils.finsight.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Tags the node when [tag] is given, and does nothing when it is not.
 *
 * It exists for components that render a value inside a node the caller cannot reach. A selector's
 * `modifier` belongs to its root — an `ExposedDropdownMenuBox` measures itself to size the menu it
 * anchors — while the value lives in the text field inside it. Compose does not merge a child's
 * text into the node that carries the resource id, so a tag on the root can be tapped but never
 * asserted against what it shows. Such a component takes a nullable tag for its value node and
 * applies it here.
 */
fun Modifier.optionalTestTag(tag: String?) = if (tag != null) testTag(tag) else this
