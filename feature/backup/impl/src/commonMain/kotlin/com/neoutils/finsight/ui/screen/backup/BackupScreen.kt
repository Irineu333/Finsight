@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.analytics.Analytics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.neoutils.finsight.extension.LocalPlatformContext
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_export_subtitle
import com.neoutils.finsight.resources.backup_export_title
import com.neoutils.finsight.resources.backup_group_export
import com.neoutils.finsight.resources.backup_group_restore
import com.neoutils.finsight.resources.backup_no_copies
import com.neoutils.finsight.resources.backup_restore_subtitle
import com.neoutils.finsight.resources.backup_restore_title
import com.neoutils.finsight.resources.backup_scope
import com.neoutils.finsight.resources.backup_screen_title
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.confirmRestore.ConfirmRestoreModal
import com.neoutils.finsight.ui.theme.SettingsTileTheme
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The local backup: a file the user takes out of the app, and a file the user brings back
 * into it.
 *
 * The two entries are the same tile the settings screen is built out of, and everything
 * around them is there because of what the operation *is* rather than to decorate it. The
 * card at the top states what the file holds and what it leaves behind, so the difference
 * between "my data" and "my app as I left it" is not discovered during a restore, and it
 * states that the app keeps no copies of its own — after this feature, moving to another
 * device recovers nothing that was not exported.
 *
 * The restore entry carries its warning as a line rather than a box: here it describes an
 * option, and a filled panel would shout at someone who has not chosen anything yet. The
 * box belongs to the confirmation, where the answer is final.
 */
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BackupViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val platformContext = LocalPlatformContext.current
    val analytics = koinInject<Analytics>()

    LaunchedEffect(Unit) {
        analytics.logScreenView("backup")
    }

    ConfirmRestoreHost(
        confirmation = uiState.confirmation,
        isRestoring = viewModel.isRestoring,
        onAction = viewModel::onAction,
    )

    Scaffold(
        modifier = Modifier.testTag("screen_backup"),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.backup_screen_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        SettingsTileTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ScopeCard()

                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.backup_group_export)) },
                ) {
                    SettingsMenuLink(
                        modifier = Modifier.testTag("backup_export"),
                        enabled = !uiState.isBusy,
                        shape = TileShape,
                        icon = { Icon(imageVector = Icons.Outlined.SaveAlt, contentDescription = null) },
                        title = { Text(text = stringResource(Res.string.backup_export_title)) },
                        subtitle = { Text(text = stringResource(Res.string.backup_export_subtitle)) },
                        action = { TileAction(isRunning = uiState.isExporting) },
                        onClick = { viewModel.onAction(BackupAction.Export(platformContext)) },
                    )
                }

                SettingsGroup(
                    title = { Text(text = stringResource(Res.string.backup_group_restore)) },
                ) {
                    SettingsMenuLink(
                        modifier = Modifier.testTag("backup_restore"),
                        enabled = !uiState.isBusy,
                        shape = TileShape,
                        icon = { Icon(imageVector = Icons.Outlined.Restore, contentDescription = null) },
                        title = { Text(text = stringResource(Res.string.backup_restore_title)) },
                        subtitle = { Text(text = stringResource(Res.string.backup_restore_subtitle)) },
                        action = { TileAction(isRunning = uiState.isVerifying) },
                        onClick = {
                            viewModel.onAction(BackupAction.ChooseFileToRestore(platformContext))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Puts the confirmation up while there is one, and takes it down when there is not.
 *
 * The sheet is rendered by the modal manager, outside this tree, so what keeps the two in
 * step is the state: the view model publishes an approved file and this shows it; the view
 * model drops it — because the replacement finished, because it failed, or because the
 * sheet was dismissed — and this dismisses whatever is still standing. Dismissing a modal
 * the manager no longer holds does nothing, which is what makes the last case harmless.
 */
@Composable
private fun ConfirmRestoreHost(
    confirmation: RestoreConfirmation?,
    isRestoring: StateFlow<Boolean>,
    onAction: (BackupAction) -> Unit,
) {
    val modalManager = LocalModalManager.current

    if (confirmation != null) {
        val modal = remember(confirmation) {
            ConfirmRestoreModal(
                confirmation = confirmation,
                isRestoring = isRestoring,
                onConfirm = { onAction(BackupAction.Restore) },
                onDiscard = { onAction(BackupAction.DiscardCandidate) },
            )
        }

        DisposableEffect(modal) {
            modalManager.show(modal)
            onDispose { modalManager.dismiss(modal) }
        }
    }
}

/** What the file carries, and what the app does not carry for the user. */
@Composable
private fun ScopeCard() {
    Surface(
        color = colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.backup_scope),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.backup_no_copies),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The chevron, or the wait it turns into while the entry's own operation runs. */
@Composable
private fun TileAction(isRunning: Boolean) {
    if (isRunning) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp,
        )
    } else {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

/** The corner every card of this app wears. */
private val TileShape = RoundedCornerShape(12.dp)
