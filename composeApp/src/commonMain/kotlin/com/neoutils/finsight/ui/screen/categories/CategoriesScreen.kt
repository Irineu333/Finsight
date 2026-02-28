@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CategoriesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            if (uiState.categories.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        modalManager.show(CategoryFormModal())
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
        if (uiState.categories.isEmpty()) {
            EmptyCategoriesState(
                onCreateDefaultCategories = {
                    onAction(CategoriesAction.CreateDefaultCategories)
                },
                onCreateManualCategory = {
                    modalManager.show(CategoryFormModal())
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val incomes = uiState.categories.filter { it.type.isIncome }
                val expenses = uiState.categories.filter { it.type.isExpense }

                if (incomes.isNotEmpty()) {
                    item(
                        key = "incomes_title"
                    ) {
                        Text(
                            text = stringResource(Res.string.categories_income),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                items(
                    items = incomes,
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

                if (expenses.isNotEmpty()) {
                    item(
                        key = "expenses_title"
                    ) {
                        Text(
                            text = stringResource(Res.string.categories_expense),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }

                items(
                    items = expenses,
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
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
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
