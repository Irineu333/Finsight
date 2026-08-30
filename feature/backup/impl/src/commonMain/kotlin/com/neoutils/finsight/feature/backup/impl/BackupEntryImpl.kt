package com.neoutils.finsight.feature.backup.impl

import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.feature.backup.api.BackupEntry
import com.neoutils.finsight.ui.navigation.backupGraph

internal class BackupEntryImpl : BackupEntry {

    context(builder: NavGraphBuilder)
    override fun register() = builder.backupGraph()
}
