package com.neoutils.finsight.backup

import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CaptureOrigin

/**
 * The version the launcher was packaged with.
 *
 * `jpackage` writes `-Djpackage.app-version=<version>` into the configuration of every
 * launcher it produces (`jdk.jpackage.internal.CfgFile`), so an installed desktop app
 * states the `packageVersion` of its own distribution without a second copy of it in the
 * sources. A run started from Gradle has no launcher and therefore no version, which is
 * exactly what it says.
 */
class JvmCaptureOrigin : CaptureOrigin {

    override val appVersion: String = System.getProperty(PACKAGED_VERSION).orEmpty()

    override val platform = BackupPlatform.DESKTOP
}

private const val PACKAGED_VERSION = "jpackage.app-version"
