package com.neoutils.finsight.database.exception

/**
 * Why a candidate file could not be checked at all. The message is English and meant for
 * the log: this module knows nothing of `UiText`, so saying any of this to a person is the
 * job of whoever offers the replacement as a feature.
 *
 * These are not refusals. A refusal is a finding about the file and arrives as a
 * `CandidateRejection`; this is the verification failing to reach a finding, and the
 * difference is the whole point of the type existing — telling someone their file is not a
 * backup because the disk filled while it was being read sends them looking for another
 * file, and no file will fix it.
 *
 * The list is short for the same reason [DatabaseRestoreError]'s is: what is left once the
 * file is out of the picture are conditions of the machine, and only one of them is
 * something the person holding the file can act on.
 */
enum class DatabaseVerificationError(val message: String) {
    NO_SPACE("There is not enough free space to carry the verification out"),
    UNKNOWN("The verification could not be carried out, for a reason this database does not recognise"),
}
