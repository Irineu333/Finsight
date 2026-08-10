package com.neoutils.finsight.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.navigate_back_content_description
import org.jetbrains.compose.resources.stringResource

/**
 * The single way a screen offers "back".
 *
 * Every screen that can be navigated away from renders this one, so that the gesture has one
 * appearance, one content description and one handle. The handle matters beyond tidiness: the
 * system back button is Android's alone — `IOSDriver.backPress()` is empty and the edge swipe does
 * not reach Compose here — so an E2E flow returns by tapping this button, and it can only do that
 * if every screen publishes the same tag.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = IconButton(
    onClick = onClick,
    modifier = modifier.testTag(BACK_BUTTON_TEST_TAG),
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(Res.string.navigate_back_content_description),
    )
}

private const val BACK_BUTTON_TEST_TAG = "top_bar_back"
