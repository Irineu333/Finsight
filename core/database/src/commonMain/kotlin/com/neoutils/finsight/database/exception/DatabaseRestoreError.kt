package com.neoutils.finsight.database.exception

/**
 * Why the database could not take on the content of a file. The message is English and
 * meant for the log: this module knows nothing of `UiText`, so saying any of this to a
 * person is the job of whoever offers the replacement as a feature.
 *
 * The list is short on purpose. The replacement runs over a file that has already been
 * approved, so the refusals left are conditions of the machine rather than findings about
 * the file, and a caller that cannot act differently on two of them gains nothing from
 * telling them apart.
 */
enum class DatabaseRestoreError(val message: String) {
    NO_SPACE("There is not enough free space to write the replaced content"),
    FOREIGN_KEYS_DISABLED(
        "Foreign keys are not enforced on this connection, and the write order relies on them"
    ),
    CYCLIC_FOREIGN_KEYS("The foreign keys form a cycle, so no order of writes satisfies them"),
    UNKNOWN("SQLite refused the replacement for a reason this database does not recognise"),
}
