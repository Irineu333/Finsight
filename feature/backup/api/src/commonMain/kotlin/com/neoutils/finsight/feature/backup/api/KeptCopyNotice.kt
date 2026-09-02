package com.neoutils.finsight.feature.backup.api

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_reversible
import com.neoutils.finsight.resources.backup_history_title
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
 *
 * *Where* is the copies screen, named by the same key its own title comes from — so the
 * sentence cannot go on pointing at a screen that has been renamed. The restore
 * confirmation, which writes the same sentence with a heading over it, resolves it the
 * same way and from the same key.
 *
 * **The glyph is what makes it a second thing rather than a longer first one.** Every one
 * of the five sheets sets its own statement in the same 16.sp `onSurfaceVariant` this
 * sentence uses, directly above it, so the two ran together as one paragraph — and they
 * are not one: the statement is what the app is about to destroy, and this is what it
 * keeps first. The mark is the whole of the difference on purpose. Colouring the sentence,
 * or boxing it, would make the promise louder than the loss it sits under, and the sheet's
 * subject is the loss.
 *
 * **The glyph is inside the text and not in a column beside it.** A row of icon-then-text
 * indents every line the sentence wraps to, not just the first — this one wraps to three on
 * a phone — so the whole block stepped away from the left edge the statement above it keeps,
 * and a paragraph set in from its neighbour reads as a quotation rather than as a remark
 * about it. As inline content the mark leads the first line and the rest of the sentence
 * comes back to the margin, which is where the eye is already reading.
 *
 * It is decorative and says so ([Icon]'s null description): the sentence beside it is
 * already the entire content, and a glyph announcing "information" before it would only
 * make a screen reader say one more word before the words that matter.
 */
@Composable
fun KeptCopyNotice(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            appendInlineContent(GLYPH_SLOT, GLYPH_FALLBACK)
            append(" ")
            append(
                stringResource(
                    Res.string.backup_confirm_reversible,
                    stringResource(Res.string.backup_history_title),
                ),
            )
        },
        inlineContent = mapOf(
            GLYPH_SLOT to InlineTextContent(
                placeholder = Placeholder(
                    // Sized in `sp`, so the mark grows with the sentence when somebody has
                    // scaled their type up rather than staying a fixed dot beside words
                    // that have outgrown it.
                    width = GLYPH_SIZE,
                    height = GLYPH_SIZE,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        ),
        fontSize = 16.sp,
        color = colorScheme.onSurfaceVariant,
        modifier = modifier.testTag("backup_kept_copy"),
    )
}

/** What stands where the mark goes, for anything that reads the string without drawing it. */
private const val GLYPH_FALLBACK = "ⓘ"

private const val GLYPH_SLOT = "kept-copy-info"

private val GLYPH_SIZE = 16.sp
