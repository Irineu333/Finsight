package com.neoutils.finsight.ui.screen.recurring

/**
 * The one selector on the recurring screen, mixing two axes on purpose: status
 * ([ACTIVE], [ARCHIVED]) and type ([EXPENSE], [INCOME]) — the same shape the
 * categories screen settled on. It replaces the two dropdowns this screen used to
 * carry (status × type, nine states) with four views.
 *
 * "Ativas" rather than "Todas" keeps it honest: archived recurrings are excluded
 * from the first three views. What is lost — archived of a single type, and "all
 * including archived" — is the same trade categories already made.
 */
enum class RecurringFilter {
    ACTIVE,
    EXPENSE,
    INCOME,
    ARCHIVED,
}
