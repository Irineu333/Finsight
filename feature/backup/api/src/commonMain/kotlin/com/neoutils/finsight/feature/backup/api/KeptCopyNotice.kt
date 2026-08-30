package com.neoutils.finsight.feature.backup.api

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_reversible
import org.jetbrains.compose.resources.stringResource

/**
 * The line a destructive confirmation carries in place of the one about permanence, once
 * the copy that makes it reversible is genuinely taken.
 *
 * It replaces nothing on its own: the sheet above it states what is going, and stops
 * claiming the loss is permanent, because with a copy kept first that claim is false. This
 * adds the fact that took its place — there is something to come back to, and where.
 *
 * **It is one sentence with one owner.** Five confirmations across three features say it,
 * and five copies of it would be five chances for one of them to promise more than the
 * vault does. Whether to show it at all is [PreventiveCoverage]'s answer and never this
 * component's.
 */
@Composable
fun KeptCopyNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.backup_confirm_reversible),
        fontSize = 16.sp,
        color = colorScheme.onSurfaceVariant,
        modifier = modifier.testTag("backup_kept_copy"),
    )
}
