package com.neoutils.finsight.domain.model

/**
 * Where a backup file was written, as a closed set of identifiers.
 *
 * It is not the diagnostic name of the platform: `getPlatform().name` answers
 * `"Android 34"`, `"Java 21.0.1"` and `"iOS 17.2"`, with the version of the system
 * baked in, which is what a crash report wants and what a stamp cannot use. A stamp is
 * written once and read by every build that ever opens the file, so what travels in it
 * is an identifier this app owns and an exhaustive `when` can translate.
 *
 * [ofId] answers `null` for anything else, and that case is real rather than defensive:
 * a file written by a build that knows a platform this one does not is still a perfectly
 * good backup, and the screen says what it can about it instead of refusing it.
 */
enum class BackupPlatform(val id: String) {
    ANDROID("android"),
    DESKTOP("desktop"),
    IOS("ios");

    companion object {
        fun ofId(id: String): BackupPlatform? = entries.firstOrNull { it.id == id }
    }
}
