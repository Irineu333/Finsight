package com.neoutils.finsight.backup

import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CaptureOrigin
import platform.Foundation.NSBundle

/**
 * The short version string of the bundle, which is what `MARKETING_VERSION` becomes and
 * what the App Store shows — read back rather than duplicated in a constant.
 */
class IosCaptureOrigin : CaptureOrigin {

    override val appVersion: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey(SHORT_VERSION) as? String ?: ""

    override val platform = BackupPlatform.IOS
}

private const val SHORT_VERSION = "CFBundleShortVersionString"
