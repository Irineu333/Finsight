package com.neoutils.finsight.domain.model

import com.neoutils.finsight.domain.error.RetireError

/**
 * Whether a category may be deleted or must be archived — the single answer to "what
 * happens when the user retires this category". Resolved in one place from the three
 * guards (movement, budget, recurring) so no screen re-derives which action applies:
 * a fourth dependent would otherwise make a screen offer a delete the domain refuses.
 */
sealed interface CategoryRetirability {

    /** No dependents: the category and its dimension can be removed outright. */
    data object Deletable : CategoryRetirability

    /** A dependent exists ([reason]): the category is kept and only archived. */
    data class MustArchive(val reason: RetireError) : CategoryRetirability
}
