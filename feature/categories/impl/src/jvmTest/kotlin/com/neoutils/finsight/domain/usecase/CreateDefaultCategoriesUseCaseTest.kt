package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateDefaultCategoriesUseCaseTest {

    @Test
    fun `seeds every default in a single insertAll batch`() = runTest {
        val repo = RecordingCategoryRepository()

        val result = CreateDefaultCategoriesUseCase(repo)()

        assertTrue(result.isRight())
        // One batch, not one insert per category: a mid-way failure can leave no partial
        // seed behind.
        assertEquals(1, repo.insertedBatches.size)
        val batch = repo.insertedBatches.single()
        assertTrue(batch.isNotEmpty())
        assertTrue(batch.any { it.type == Category.Type.INCOME })
        assertTrue(batch.any { it.type == Category.Type.EXPENSE })
    }
}
