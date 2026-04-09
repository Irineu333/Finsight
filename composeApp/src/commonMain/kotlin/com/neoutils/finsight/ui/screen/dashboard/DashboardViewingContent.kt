@file:OptIn(ExperimentalFoundationApi::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.interceptLongPress
import com.neoutils.finsight.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DashboardViewingContent(
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
                            .interceptLongPress { onAction(DashboardAction.EnterEditMode) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DashboardLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun DashboardEmptyContent(
    onAction: (DashboardAction) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.dashboard_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onAction(DashboardAction.EnterEditMode) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(Res.string.dashboard_empty_action))
            }
        }
    }
}
