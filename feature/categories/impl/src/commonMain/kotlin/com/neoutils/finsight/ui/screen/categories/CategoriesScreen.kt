@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.categories

import com.neoutils.finsight.ui.util.isWideWindow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.ui.component.CategoryCard
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.categories_create_default
import com.neoutils.finsight.resources.categories_create_manual
import com.neoutils.finsight.resources.categories_empty
import com.neoutils.finsight.resources.categories_empty_filter
import com.neoutils.finsight.resources.categories_expense
import com.neoutils.finsight.resources.categories_filter_active
import com.neoutils.finsight.resources.categories_filter_archived
import com.neoutils.finsight.resources.categories_income
import com.neoutils.finsight.resources.categories_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CategoriesViewModel = koinViewModel()
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        analytics.logScreenView("categories")
    }

    CategoriesContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun CategoriesContent(
    uiState: CategoriesUiState,
    onNavigateBack: () -> Unit,
    onAction: (CategoriesAction) -> Unit,
) {
    val modalManager = LocalModalManager.current
    val detailController = LocalDetailPaneController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.categories_title))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                    actionIconContentColor = colorScheme.onBackground,
                ),
                navigationIcon = {
                    if (!isWideWindow()) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    // The view selector lives in the top bar, as a dropdown — the same
                    // place and shape the accounts screen keeps its month control.
                    if (uiState is CategoriesUiState.Content) {
                        FilterSelector(
                            selected = uiState.filter,
                            onSelect = { onAction(CategoriesAction.SelectFilter(it)) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState is CategoriesUiState.Content) {
                FloatingActionButton(
                    onClick = {
                        modalManager.show(CategoryFormModal(initialType = uiState.filter.fabInitialType))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            CategoriesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is CategoriesUiState.Empty -> {
                EmptyDatabaseState(
                    onCreateDefaultCategories = {
                        onAction(CategoriesAction.CreateDefaultCategories)
                    },
                    onCreateManualCategory = {
                        modalManager.show(CategoryFormModal(initialType = uiState.filter.fabInitialType))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is CategoriesUiState.Content if uiState.sections.isEmpty() -> {
                EmptyFilterState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is CategoriesUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.sections.forEachIndexed { index, section ->
                        section.header?.let { header ->
                            item(key = "header_$index") {
                                SectionHeader(
                                    text = stringResource(header),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        items(
                            items = section.categories,
                            key = { it.id },
                        ) { category ->
                            CategoryCard(
                                category = category,
                                onClick = {
                                    detailController.show(ViewCategoryModal(category.id))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSelector(
    selected: CategoryFilter,
    onSelect: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            TextButton(
                onClick = { menuExpanded = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Unspecified,
                ),
            ) {
                Text(text = stringResource(selected.label))
                Spacer(modifier = Modifier.width(4.dp))
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
            CategoryFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(stringResource(filter.label)) },
                    trailingIcon = if (selected == filter) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        onSelect(filter)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

private val CategoryFilter.label: StringResource
    get() = when (this) {
        CategoryFilter.ACTIVE -> Res.string.categories_filter_active
        CategoryFilter.EXPENSE -> Res.string.categories_expense
        CategoryFilter.INCOME -> Res.string.categories_income
        CategoryFilter.ARCHIVED -> Res.string.categories_filter_archived
    }

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp),
    )
}

/** The big CTA, earned only by a database with no category at all (design D10). */
@Composable
private fun EmptyDatabaseState(
    onCreateDefaultCategories: () -> Unit,
    onCreateManualCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.categories_empty),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onCreateDefaultCategories,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = stringResource(Res.string.categories_create_default),
                )
            }

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = onCreateManualCategory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.categories_create_manual))
            }
        }
    }
}

/** A filter with nothing to show, database not empty: a quiet note, no CTA (design D10). */
@Composable
private fun EmptyFilterState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Category,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(40.dp),
        )
        Text(
            text = stringResource(Res.string.categories_empty_filter),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
    }
}
