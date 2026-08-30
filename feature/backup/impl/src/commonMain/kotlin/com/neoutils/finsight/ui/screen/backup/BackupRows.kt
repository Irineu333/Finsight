package com.neoutils.finsight.ui.screen.backup

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The beat both backup lists are laid out on.
 *
 * The feature is two lists — the settings of the vault, and the copies it kept — and they
 * are two views of one subject, reached one from the other. What keeps them from looking
 * like two apps is here: the corner a card wears, the gap between rows, and the wider gap
 * that opens a group.
 */

/** The corner every card of this app wears. */
internal val TileShape = RoundedCornerShape(12.dp)

/** What the tile library puts between the rows of one group. */
internal val RowGap = 8.dp

/** What the settings screen puts between one group and the next. */
internal val GroupGap = 20.dp

/**
 * The rows of one list, where a group is separated from the one above it.
 *
 * `Arrangement.spacedBy` — what the settings screen's `Column` uses — never puts space
 * before its first child, and a lazy list has one arrangement for every row. So the rule is
 * applied to the row's *position* instead, and to the position rather than to a named row,
 * because which row comes first changes with the state.
 */
internal class BackupRows(private val scope: LazyListScope) {

    private var isFirstRow = true

    fun row(
        key: String,
        opensGroup: Boolean = false,
        content: @Composable LazyItemScope.(Modifier) -> Unit,
    ) {
        val leading = if (opensGroup && !isFirstRow) GroupGap - RowGap else 0.dp
        isFirstRow = false

        scope.item(key = key) { content(Modifier.padding(top = leading).animateItem()) }
    }
}

/** Lays the rows [content] declares out on that beat, in the list this is called on. */
internal fun LazyListScope.backupRows(content: BackupRows.() -> Unit) {
    BackupRows(this).content()
}
