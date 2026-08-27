package com.neoutils.finsight.ui.screen.recurring

/**
 * The one selector on the recurring screen, on **one axis**: the nature of the money.
 *
 * One axis and not two. A control that sometimes narrows the list and sometimes changes
 * what the screen *is* leaves the user unable to tell which of the two it just did — so
 * archived recurrings have a destination of their own, and what stays here is transversal
 * to the sections: it narrows each of them without touching how the list is organised.
 *
 * [ALL] and not a name that promises a cut by status: in a monthly list what is shown is
 * non-archived by construction — an archived template generates no cycle, in any month —
 * so there is no status left to cut by.
 */
enum class RecurringFilter {
    ALL,
    EXPENSE,
    INCOME,
}
