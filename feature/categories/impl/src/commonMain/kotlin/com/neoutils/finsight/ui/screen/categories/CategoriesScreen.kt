@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.neoutils.finsight.ui.screen.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.component.CategoryCard
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.categories_create_default
import com.neoutils.finsight.resources.categories_create_manual
import com.neoutils.finsight.resources.categories_empty
import com.neoutils.finsight.resources.categories_expense
import com.neoutils.finsight.resources.categories_income
import com.neoutils.finsight.resources.categories_title
import kotlinx.coroutines.flow.distinctUntilChanged
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(Res.string.categories_title))
                },
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
        floatingActionButton = {
            if (uiState is CategoriesUiState.Content) {
                FloatingActionButton(
                    onClick = {
                        modalManager.show(CategoryFormModal(initialType = uiState.selectedType))
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
                EmptyCategoriesState(
                    onCreateDefaultCategories = {
                        onAction(CategoriesAction.CreateDefaultCategories)
                    },
                    onCreateManualCategory = {
                        modalManager.show(CategoryFormModal(initialType = uiState.selectedType))
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is CategoriesUiState.Content -> {
                val tabs = listOf(Category.Type.EXPENSE, Category.Type.INCOME)
                val selectedTabIndex = tabs.indexOf(uiState.selectedType).coerceAtLeast(0)

                val pagerState = rememberPagerState(
                    initialPage = selectedTabIndex,
                    pageCount = { tabs.size },
                )

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            onAction(CategoriesAction.SelectType(tabs[page]))
                        }
                }

                LaunchedEffect(selectedTabIndex) {
                    if (pagerState.currentPage != selectedTabIndex) {
                        pagerState.animateScrollToPage(selectedTabIndex)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, type ->
                            val title = when (type) {
                                Category.Type.EXPENSE -> stringResource(Res.string.categories_expense)
                                Category.Type.INCOME -> stringResource(Res.string.categories_income)
                            }

                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = {
                                    onAction(CategoriesAction.SelectType(type))
                                },
                                text = {
                                    Text(text = title)
                                }
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val pageCategories = uiState.categories.filter { it.type == tabs[page] }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = pageCategories,
                                key = { it.id },
                            ) { category ->
                                CategoryCard(
                                    category = category,
                                    onClick = {
                                        modalManager.show(ViewCategoryModal(category))
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
}

@Composable
private fun EmptyCategoriesState(
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
