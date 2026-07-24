@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.modal.viewAccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_selector_label
import com.neoutils.finsight.resources.account_unarchive
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.DetailErrorState
import com.neoutils.finsight.ui.component.DetailLoadingState
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.OutlinedActionButton
import com.neoutils.finsight.util.AppIcon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ViewAccountModal(
    private val accountId: Long,
) : AdaptiveModal() {

    // Both slots resolve the same ViewModel and collect the same state under this
    // modal as their ViewModelStoreOwner — see ViewCreditCardModal.
    @Composable
    private fun rememberViewState(): Pair<ViewAccountViewModel, ViewAccountUiState> {
        val viewModel = koinViewModel<ViewAccountViewModel> { parametersOf(accountId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        return viewModel to uiState
    }

    @Composable
    override fun DetailContent() {
        val detailController = LocalDetailPaneController.current
        val (viewModel, uiState) = rememberViewState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ViewAccountEvent.Dismiss -> detailController.dismiss()
                }
            }
        }

        when (uiState) {
            ViewAccountUiState.Loading -> DetailLoadingState()
            ViewAccountUiState.Error -> DetailErrorState()
            is ViewAccountUiState.Content -> ContentBody(uiState)
        }
    }

    @Composable
    private fun ContentBody(uiState: ViewAccountUiState.Content) {
        val account = uiState.account

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = AppIcon.fromKey(account.iconKey).icon,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface,
                    )
                    // No balance for an archived account (always zero); the identity —
                    // icon, name and the fact it is an account — is the whole detail.
                    Text(
                        text = stringResource(Res.string.account_selector_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    override fun DetailActions() {
        val (viewModel, uiState) = rememberViewState()
        uiState as? ViewAccountUiState.Content ?: return

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // This detail is reached only from the archived list, so the only offer is
            // unarchiving — retiring an active account stays in the AccountActions row
            // on the accounts screen (design D7).
            OutlinedActionButton(
                label = stringResource(Res.string.account_unarchive),
                icon = Icons.Default.Unarchive,
                contentColor = colorScheme.primary,
                onClick = { viewModel.onAction(ViewAccountAction.Unarchive) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
