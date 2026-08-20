package com.neoutils.finsight.database.exception

/**
 * Why a capture could not produce its file. The message is English and meant for the
 * log: this module knows nothing of `UiText`, so saying any of this to a person is the
 * job of whoever offers the capture as a feature.
 */
enum class DatabaseCaptureError(val message: String) {
    DESTINATION_EXISTS("The destination path already holds a file with content"),
    NO_SPACE("There is not enough free space to write the captured file"),
    STATEMENT_IN_PROGRESS("A statement is still running on the connection the capture uses"),
    UNKNOWN("SQLite refused the capture for a reason this database does not recognise"),
}
