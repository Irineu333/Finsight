@file:OptIn(ExperimentalFoundationApi::class)

package com.neoutils.finsight.feature.dashboard.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.feature.dashboard.action.DashboardAction
import com.neoutils.finsight.feature.dashboard.constant.DashboardComponentConfig
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.dashboard.extension.interceptLongPress
import com.neoutils.finsight.feature.dashboard.resources.*
import com.neoutils.finsight.feature.dashboard.state.DashboardUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun DashboardViewingContent(
    state: DashboardUiState.Viewing,
    openTransactions: (Transaction.Type?, Transaction.Target?) -> Unit,
    onAction: (DashboardAction) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 32.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.showEditTip) {
            item(key = "edit_tip") {
                DashboardEditTip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .animateItem(),
                )
            }
        }

        state.items.forEach { variant ->
            val config = variant.config
            val topSpacing = config[DashboardComponentConfig.TOP_SPACING] == "true"

            item(key = variant.key) {
                Column(modifier = Modifier.animateItem()) {
                    if (topSpacing) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    DashboardComponentContent(
                        variant = variant,
                        openTransactions = openTransactions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .interceptLongPress {
                                onAction(DashboardAction.EnterEditMode)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardEditTip(
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.dashboard_edit_tip_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(Res.string.dashboard_edit_tip_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}