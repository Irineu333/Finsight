@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.mcpActivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_activity_history_title
import com.neoutils.finsight.ui.screen.mcp.McpActivityClearButton
import com.neoutils.finsight.ui.screen.mcp.McpActivityEmpty
import com.neoutils.finsight.ui.screen.mcp.McpActivityRow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole agent log — the history the section's glance leads to, and where it is cleared.
 *
 * The rows are the same ones the section shows, built by the same mapping: the glance and the log
 * cannot describe the same act differently.
 */
@Composable
internal fun McpActivityScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: McpActivityViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag("screen_mcp_activity"),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.mcp_activity_history_title)) },
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
                actions = {
                    McpActivityClearButton(
                        enabled = uiState.entries.isNotEmpty(),
                        onClear = viewModel::onClear,
                    )
                },
            )
        },
    ) { padding ->
        if (uiState.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                McpActivityEmpty(modifier = Modifier.padding(horizontal = 32.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = uiState.entries, key = { it.id }) { entry ->
                McpActivityRow(entry = entry)
                HorizontalDivider(color = colorScheme.outlineVariant)
            }
        }
    }
}
