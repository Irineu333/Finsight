package com.neoutils.finsight.ui.screen.recurring

/**
 * The one selector on the recurring screen, on **one axis**: the nature of the money.
 *
 * It used to mix two — status ([ARCHIVED]) and type — so the same control sometimes
 * narrowed the list and sometimes changed what the screen was. With archived recurrings
 * in a destination of their own, only the nature is left, and it is transversal to the
 * sections: it narrows each of them without touching how the list is organised.
 *
 * [ALL] rather than the old "Ativas": in a monthly list what is shown is non-archived by
 * construction — an archived template generates no cycle, in any month — so a name that
 * promised a status cut would be promising one that no longer exists.
 */
enum class RecurringFilter {
    ALL,
    EXPENSE,
    INCOME,
}
