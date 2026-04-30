@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.feature.support.impl.resources.*
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.supportIssueForm.CreateSupportIssueModal
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SupportScreen(
    onNavigateBack: () -> Unit = {},
    onOpenIssue: (String) -> Unit = {},
    viewModel: SupportViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val modalManager = LocalModalManager.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("support")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.support_title))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
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
                    SupportActiveFilterMenu(
                        showActive = uiState.showActive,
                        onFilterSelected = { viewModel.setFilter(it) },
                    )
                },
            )
        },
        floatingActionButton = {
            if (uiState is SupportUiState.Content) {
                FloatingActionButton(
                    onClick = {
                        modalManager.show(
                            CreateSupportIssueModal(
                                onSubmit = { draft ->
                                    viewModel.createIssue(
                                        draft = draft,
                                    )
                                },
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { paddingValues ->
        when (uiState) {
            is SupportUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is SupportUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "overview") {
                        SupportOverviewCard(uiState = uiState)
                    }

                    items(
                        items = uiState.issues,
                        key = { it.id },
                    ) { issue ->
                        SupportIssueCard(
                            issue = issue,
                            onClick = { onOpenIssue(issue.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            is SupportUiState.Empty -> {
                EmptySupportState(
                    onCreateIssue = {
                        modalManager.show(
                            CreateSupportIssueModal(
                                onSubmit = { draft ->
                                    viewModel.createIssue(
                                        draft = draft,
                                    )
                                },
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun EmptySupportState(
    onCreateIssue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }

        Text(
            text = stringResource(Res.string.support_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(Res.string.support_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        androidx.compose.material3.Button(
            onClick = onCreateIssue,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(text = stringResource(Res.string.support_empty_cta))
        }
    }
}

@Composable
private fun SupportOverviewCard(
    uiState: SupportUiState.Content,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.support_overview_waiting, uiState.waitingSupportCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.support_overview_total, uiState.issues.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SupportActiveFilterMenu(
    showActive: Boolean,
    onFilterSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            TextButton(
                onClick = { menuExpanded = true },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Unspecified),
            ) {
                Text(
                    text = stringResource(
                        if (showActive) Res.string.support_filter_active
                        else Res.string.support_filter_inactive
                    ),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            listOf(
                true to stringResource(Res.string.support_filter_active),
                false to stringResource(Res.string.support_filter_inactive),
            ).forEach { (active, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = if (showActive == active) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        onFilterSelected(active)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}
