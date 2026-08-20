package com.neoutils.finsight.backup

import android.content.Context
import android.content.pm.PackageManager
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CaptureOrigin

/**
 * The version the package itself declares, which is the one the store installed and the
 * one `versionName` was built with — read back rather than duplicated in a constant.
 *
 * `versionName` is nullable in the framework and the package of a running app is always
 * installed, so the failure is theoretical; it is answered with nothing rather than with
 * a guess, because a stamp is read by someone deciding whether to overwrite an archive.
 */
class AndroidCaptureOrigin(private val context: Context) : CaptureOrigin {

    override val appVersion: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (cause: PackageManager.NameNotFoundException) {
            ""
        }

    override val platform = BackupPlatform.ANDROID
}
