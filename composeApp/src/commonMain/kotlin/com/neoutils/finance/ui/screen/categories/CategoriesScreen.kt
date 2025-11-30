@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finance.ui.screen.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finance.data.Category
import com.neoutils.finance.ui.component.CategoryCard
import com.neoutils.finance.ui.component.LocalModalManager
import com.neoutils.finance.ui.modal.AddCategoryModal
import com.neoutils.finance.ui.modal.ViewCategoryModal
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: CategoriesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CategoriesContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun CategoriesContent(
    uiState: CategoriesUiState,
    onNavigateBack: () -> Unit
) {
    val modalManager = LocalModalManager.current

    Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(text = "Categorias")
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
        FloatingActionButton(
            onClick = {
                modalManager.show(AddCategoryModal())
            },
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )
        }
    }
) { paddingValues ->
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
                item {
                    Text(
                        text = "Receitas",
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
                item {
                    Text(
                        text = "Despesas",
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