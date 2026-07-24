package com.neoutils.finsight.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.vector.ImageVector
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_archive
import com.neoutils.finsight.resources.retire_action_delete
import org.jetbrains.compose.resources.StringResource

/**
 * How a screen offers retiring an account, a card or a category.
 *
 * The *outcome* is the ledger's decision and belongs to the use cases: an account
 * with movement cannot be removed without breaking the entries that reference it,
 * and each use case refuses the other's case. What a screen decides is how to
 * **name and show** the action — and it carries its own label and icon precisely
 * so that no screen re-derives them. Two screens deriving the same presentation
 * separately is what let accounts and cards drift to different icons.
 */
enum class RetireAction(
    val label: StringResource,
    val icon: ImageVector,
) {
    DELETE(label = Res.string.retire_action_delete, icon = Icons.Default.Delete),
    ARCHIVE(label = Res.string.retire_action_archive, icon = Icons.Default.Archive),
}

/**
 * Maps "can this be deleted?" to the action a screen offers: something that must be
 * preserved is archived, everything else is deleted.
 *
 * For an account or card that means movement (entries referencing it). For a
 * category it is broader — a budget or a recurring still pointing at it also blocks
 * deletion (`DeleteCategoryUseCase`), so those must archive too, or the screen would
 * offer a delete the use case refuses.
 */
fun retireActionOf(mustPreserve: Boolean): RetireAction =
    if (mustPreserve) RetireAction.ARCHIVE else RetireAction.DELETE
