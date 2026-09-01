package com.neoutils.finsight.ui.screen.backup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The two boxes this feature makes statements in.
 *
 * A backup sheet only ever says two kinds of thing: what a file holds, which is a column of
 * labelled values, and what the settings or the vault amount to, which is one reading with a
 * tone. Both are stated in more than one place — the sheet about a kept copy and the
 * confirmation before a restore describe the same file, and the settings sheet and that same
 * confirmation both put a consequence in a tinted box — so they are one component each. Two
 * copies of a fact box is how the same four counts came to be laid out two different ways in
 * one feature.
 *
 * Every box is a step recessed from the sheet it sits on, and the ground is `background`
 * rather than `surfaceContainer`: the two names are one colour in this theme, so a card
 * painted the second inside a sheet painted `surface` is a card nobody can see.
 */

/**
 * What a file holds, as a column of labelled values.
 *
 * The frame is drawn before the values are known, so the box has one shape and the figures
 * fill into it: a bar where a value is still coming, a dash where it never will. A box that
 * grew by three rows the moment a file answered would move whatever is under the reader's
 * thumb.
 *
 * @param tag the id a driver reaches the box by, which differs per sheet — the box is the
 * same component, the statement is not.
 */
@Composable
internal fun FactBox(
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = colorScheme.background,
        shape = TileShape,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * One label and what stands opposite it: the figure, or the bar that says one is coming.
 *
 * The figures are set in tabular numerals so that values stacked in one column line up on
 * their digits — a box of numbers that does not is what makes a set of facts read as a dump
 * of strings.
 *
 * @param value null while the value is still being read. A value that will never arrive is
 * [MissingValue], which is a value: the row keeps its label and says so.
 * @param tag names the node that renders the **figure**, not the row that holds it. A tag on
 * the row reaches an E2E driver as an element with no text of its own — the label and the
 * value are children of it — so a flow could assert the row exists and never the number in
 * it, which is the assertion worth making (`.maestro/README.md` §5.2, padrão 2). Its absence
 * while the value is still being read is not a gap either: [PendingValue] carries a tag of
 * its own, so the two states stay told apart.
 */
@Composable
internal fun FactRow(label: String, value: String?, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (value == null) {
            PendingValue()
        } else {
            Text(
                text = value,
                style = typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = TabularFigures,
                ),
                color = colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

/**
 * Where a value will be. It takes the height of the line it stands in, so the row it holds
 * open is exactly the row the figure will occupy.
 */
@Composable
private fun PendingValue() {
    Box(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .width(56.dp)
            .height(10.dp)
            .background(
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            )
            .testTag("backup_copy_facts_pending"),
    )
}

/**
 * A statement a sheet makes about what its settings, or its action, come to: the reading on
 * one line and, where there is a second thing to say about it, why it comes out that way on
 * the next.
 *
 * [hint] is null where only one reading has a source. An empty line would be the box
 * asserting that something is missing, when what is there is the whole of what can be said.
 *
 * The warning tone paints its own tint and needs no icon beside it: the amber *is* the
 * signal, and a glyph would be a second mark for the same thing.
 *
 * **It is one box in every state, and that is what lets a change be carried rather than
 * cut.** Two boxes chosen by a branch would be two nodes with nothing to animate between,
 * and — since the states are told apart by [tag] — crossing between them would put both
 * names in the tree at once, which is a thing a driver can catch. Here the reading, the tone
 * and the tag are parameters of the same box, so exactly one name is ever present and the
 * tint, the words and the height all move together.
 */
@Composable
internal fun OutcomeBox(
    value: String,
    hint: String?,
    tone: Color,
    container: Color,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val ground by animateColorAsState(container, label = "outcome_ground")
    val valueTone by animateColorAsState(tone, label = "outcome_tone")

    Surface(
        color = ground,
        shape = TileShape,
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        // The words fade in place and the tint ramps under them, so a change reads as this
        // box answering again rather than as a repaint of the area below it. The height a
        // second line takes belongs to that same crossing — `AnimatedContent` carries its own
        // size change — which is what keeps the box from resizing around text that has
        // already swapped.
        AnimatedContent(
            targetState = value to hint,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "outcome",
        ) { (currentValue, currentHint) ->
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = currentValue,
                    style = typography.titleSmall,
                    color = valueTone,
                )
                if (currentHint != null) {
                    Text(
                        text = currentHint,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** What stands where a figure would, on a file that could not be opened to produce one. */
internal const val MissingValue = "—"
