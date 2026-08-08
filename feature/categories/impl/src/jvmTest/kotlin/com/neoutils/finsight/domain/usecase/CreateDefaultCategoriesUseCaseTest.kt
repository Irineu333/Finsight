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

    @Test
    fun `the seed offers no investments category`() = runTest {
        val repo = RecordingCategoryRepository()

        CreateDefaultCategoriesUseCase(repo)()

        // Thirteen, not fourteen: "Investimentos" left the templates because it is
        // conceptually the yield category, which the app now provides itself on the
        // first yielding account — the two would live side by side as duplicates.
        assertEquals(13, repo.insertedBatches.single().size)
        // A system category is never seeded: it exists only once something needs it.
        assertTrue(repo.insertedBatches.single().none { it.systemKey != null })
    }
}
