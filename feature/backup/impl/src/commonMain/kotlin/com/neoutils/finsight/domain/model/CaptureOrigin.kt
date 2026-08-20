package com.neoutils.finsight.domain.model

/**
 * What this install stamps into every file it captures: which app wrote it, and where.
 *
 * `:core:database` asks for both and knows neither — it captures a database, and which
 * app is running it is not a fact about a database. They arrive from the platform each
 * one is a fact of, which is why this is resolved by injection rather than read from a
 * constant the release process would have to remember to update: the version already
 * lives in each platform's own build, and reading it back is what keeps a single copy of
 * it.
 */
interface CaptureOrigin {

    /**
     * The version the running app declares, or empty when the platform states none —
     * the case of a desktop build launched from the sources rather than from an
     * installed package. Empty is honest and is rendered as nothing at all; a made-up
     * number would be read as a fact.
     */
    val appVersion: String

    val platform: BackupPlatform
}
