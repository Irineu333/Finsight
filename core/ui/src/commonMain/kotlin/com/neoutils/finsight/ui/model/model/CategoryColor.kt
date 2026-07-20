package com.neoutils.finsight.ui.model

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.theme.Expense
import com.neoutils.finsight.ui.theme.Income

/**
 * The colour a category reads as, in every place it is drawn.
 *
 * Green and red say "this is money coming in / going out" — a claim about an
 * account that still takes part. An archived category takes part in nothing new,
 * so it reads muted: present in the history it belongs to, and visibly out of
 * circulation. The theme's `onSurfaceVariant` carries that in both light and dark.
 *
 * This exists as one owner because the rule was already spelled out in five
 * places by direction alone; adding the archived case to each would have been the
 * fifth copy of a rule with no owner — the same drift that gave accounts and
 * cards different icons for the same action.
 */
val Category.displayColor: Color
    @Composable
    @ReadOnlyComposable
    get() = when {
        isArchived -> colorScheme.onSurfaceVariant
        type == Category.Type.INCOME -> Income
        type == Category.Type.EXPENSE -> Expense
        else -> colorScheme.onSurface
    }

/**
 * The tint for a category icon drawn *inside a transaction*.
 *
 * The transaction keeps its own colour — it is still an expense or an income, and
 * archiving the category does not change what happened. Only the category's own
 * mark reads muted, so the icon says "this category is out of circulation"
 * without recolouring the movement around it.
 */
@Composable
@ReadOnlyComposable
fun categoryTint(default: Color, isCategoryArchived: Boolean): Color =
    if (isCategoryArchived) colorScheme.onSurfaceVariant else default
